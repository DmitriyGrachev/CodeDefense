package dev.codedefense.change;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.SourceLineRange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.scanner.FilePrioritizer;
import dev.codedefense.scanner.ProjectFileFilter;
import dev.codedefense.scanner.SecretRedactor;
import dev.codedefense.scanner.SnapshotBudget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Builds a bounded project snapshot exclusively from staged Git diff hunks. */
public final class StagedChangeContextBuilder {
    private static final int MAXIMUM_DIFF_PREFIX_BYTES = 16 * 1024;

    private final CodeDefenseConfig config;
    private final ProjectFileFilter filter;
    private final FilePrioritizer prioritizer;
    private final SecretRedactor redactor;
    private final SnapshotBudget budget;

    public StagedChangeContextBuilder() {
        this(CodeDefenseConfig.defaults());
    }

    StagedChangeContextBuilder(CodeDefenseConfig config) {
        this(config, new ProjectFileFilter(), new FilePrioritizer(), new SecretRedactor(), new SnapshotBudget());
    }

    StagedChangeContextBuilder(CodeDefenseConfig config, ProjectFileFilter filter,
            FilePrioritizer prioritizer, SecretRedactor redactor, SnapshotBudget budget) {
        this.config = config;
        this.filter = filter;
        this.prioritizer = prioritizer;
        this.redactor = redactor;
        this.budget = budget;
    }

    public ProjectSnapshot build(CapturedStagedChange captured) {
        return buildHunkSnapshot(captured);
    }

    private ProjectSnapshot buildHunkSnapshot(CapturedStagedChange captured) {
        Map<Path, StagedChangeFile> declaredFiles = declaredFiles(captured.change().files());
        Map<Path, List<StagedHunk>> hunksByPath = hunksByPath(captured.hunks(), declaredFiles);
        List<SourceFile> candidates = hunkCandidates(hunksByPath);
        int ignored = captured.change().files().size() - candidates.size();
        ScanSummary summary = new ScanSummary(captured.change().repositoryRoot(), captured.change().files().size(),
                ignored, candidates);
        String projectName = repositoryName(captured.change().repositoryRoot());
        StringBuilder prompt = new StringBuilder(header(captured, projectName));
        appendDiffPrefix(prompt, canonicalDiffMetadata(captured.change().files()));

        List<ProjectSnapshot.SelectedFile> selected = new ArrayList<>();
        int redactions = 0;
        for (SourceFile candidate : prioritizer.prioritize(candidates)) {
            if (selected.size() >= config.maximumSelectedFiles()) {
                break;
            }
            int remaining = config.maximumSnapshotBytes() - budget.utf8Bytes(prompt.toString());
            int unitLimit = Math.min(remaining, config.maximumFileBlockBytes());
            if (unitLimit <= 1) {
                break;
            }
            StringBuilder unit = new StringBuilder();
            List<SourceLineRange> ranges = new ArrayList<>();
            boolean truncated = false;
            for (StagedHunk hunk : hunksByPath.get(candidate.relativePath())) {
                int available = unitLimit - 1 - budget.utf8Bytes(unit.toString()) - (unit.isEmpty() ? 0 : 1);
                if (available <= 0) {
                    truncated = true;
                    break;
                }
                SecretRedactor.RedactionResult redacted = redactor.redact(hunk.unifiedContent());
                FittedHunk fitted = fitHunk(hunk, redacted.content(), available);
                if (fitted == null) {
                    truncated = true;
                    break;
                }
                if (!unit.isEmpty()) {
                    unit.append('\n');
                }
                unit.append(fitted.content());
                ranges.addAll(fitted.evidenceRanges());
                truncated |= fitted.truncated();
            }
            if (unit.isEmpty() || budget.utf8Bytes(unit + "\n") > unitLimit) {
                continue;
            }
            prompt.append(unit).append('\n');
            redactions += redactionMarkers(unit.toString());
            List<SourceLineRange> evidenceRanges = mergeRanges(ranges);
            selected.add(new ProjectSnapshot.SelectedFile(candidate.relativePath(), includedLineCount(evidenceRanges),
                    evidenceRanges, truncated, budget.utf8Bytes(unit + "\n")));
        }
        if (selected.isEmpty()) {
            throw new EmptyProjectSnapshotException();
        }
        String promptContent = prompt.toString();
        int promptBytes = budget.utf8Bytes(promptContent);
        if (promptBytes > config.maximumSnapshotBytes()) {
            throw new IllegalStateException("Snapshot exceeds configured byte limit");
        }
        return new ProjectSnapshot(captured.change().repositoryRoot(), projectName, "Staged Git change", summary,
                selected, promptContent, promptBytes, redactions);
    }

    private Map<Path, List<StagedHunk>> hunksByPath(List<StagedHunk> hunks,
            Map<Path, StagedChangeFile> declaredFiles) {
        Map<Path, List<StagedHunk>> byPath = new HashMap<>();
        for (StagedHunk hunk : hunks) {
            StagedChangeFile declared = declaredFiles.get(hunk.file().path());
            if (!hunk.file().equals(declared)) {
                continue;
            }
            byPath.computeIfAbsent(hunk.file().path(), ignored -> new ArrayList<>()).add(hunk);
        }
        return Map.copyOf(byPath);
    }

    private List<SourceFile> hunkCandidates(Map<Path, List<StagedHunk>> hunksByPath) {
        return hunksByPath.values().stream()
                .map(List::getFirst)
                .filter(hunk -> !filter.isExcludedFile(hunk.file().path()))
                .filter(hunk -> !containsExcludedDirectory(hunk.file().path()))
                .filter(hunk -> filter.isSupportedFile(hunk.file().path()))
                .map(hunk -> new SourceFile(hunk.file().path(), hunk.unifiedContent().getBytes(StandardCharsets.UTF_8).length))
                .sorted(Comparator.comparing(source -> portable(source.relativePath())))
                .toList();
    }

    private FittedHunk fitHunk(StagedHunk hunk, String content, int byteLimit) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        while (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (byteLimit <= 0 || normalized.isEmpty()) {
            return null;
        }
        if (budget.utf8Bytes(renderHunkBlock(hunk, normalized, hunk.truncated())) <= byteLimit) {
            return fittedHunk(hunk, normalized, hunk.truncated());
        }
        StringBuilder included = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            String candidate = included.isEmpty() ? line : included + "\n" + line;
            if (budget.utf8Bytes(renderHunkBlock(hunk, candidate, true)) <= byteLimit) {
                included.setLength(0);
                included.append(candidate);
                continue;
            }
            String prefix = longestHunkLinePrefix(hunk, included.toString(), line, byteLimit);
            if (!prefix.isEmpty()) {
                if (!included.isEmpty()) {
                    included.append('\n');
                }
                included.append(prefix);
            }
            break;
        }
        if (included.isEmpty()) {
            return null;
        }
        return fittedHunk(hunk, included.toString(), true);
    }

    private String longestHunkLinePrefix(StagedHunk hunk, String included, String line, int byteLimit) {
        StringBuilder prefix = new StringBuilder();
        for (int offset = 0; offset < line.length();) {
            int codePoint = line.codePointAt(offset);
            prefix.appendCodePoint(codePoint);
            String candidate = included.isEmpty() ? prefix.toString() : included + "\n" + prefix;
            if (budget.utf8Bytes(renderHunkBlock(hunk, candidate, true)) > byteLimit) {
                prefix.setLength(prefix.length() - Character.charCount(codePoint));
                break;
            }
            offset += Character.charCount(codePoint);
        }
        return prefix.toString();
    }

    private String renderHunkBlock(StagedHunk hunk, String content, boolean truncated) {
        String kind = hunk.file().status() == StagedFileStatus.DELETED ? "HEAD_HUNK" : "STAGED_HUNK";
        String state = hunk.file().status() == StagedFileStatus.DELETED
                ? "DELETED_FROM_INDEX" : "STAGED_INDEX";
        StringBuilder rendered = new StringBuilder();
        rendered.append(kind).append(": ").append(portable(hunk.file().path())).append('\n')
                .append("EVIDENCE_STATE: ").append(state).append('\n')
                .append("STATUS: ").append(hunk.file().status()).append('\n')
                .append("OLD_LINES: ").append(range(hunk.oldStartLine(), hunk.oldLineCount())).append('\n')
                .append("NEW_LINES: ").append(range(hunk.newStartLine(), hunk.newLineCount())).append('\n')
                .append("TRUNCATED: ").append(truncated).append('\n');
        int oldLine = hunk.oldStartLine();
        int newLine = hunk.newStartLine();
        for (String line : content.split("\n", -1)) {
            String value = line.substring(1);
            switch (line.charAt(0)) {
                case ' ' -> {
                    rendered.append("OLD ").append(oldLine++).append(" | ").append(value).append('\n');
                    rendered.append("NEW ").append(newLine++).append(" | ").append(value).append('\n');
                }
                case '-' -> rendered.append("OLD ").append(oldLine++).append(" | ").append(value).append('\n');
                case '+' -> rendered.append("NEW ").append(newLine++).append(" | ").append(value).append('\n');
                default -> throw new IllegalArgumentException("Hunk content has an invalid line prefix");
            }
        }
        rendered.setLength(rendered.length() - 1);
        return rendered.toString();
    }

    private FittedHunk fittedHunk(StagedHunk hunk, String rawContent, boolean truncated) {
        return new FittedHunk(renderHunkBlock(hunk, rawContent, truncated), truncated,
                retainedRanges(hunk, rawContent));
    }

    private List<SourceLineRange> retainedRanges(StagedHunk hunk, String content) {
        TreeSet<Integer> retainedLines = new TreeSet<>();
        int oldLine = hunk.oldStartLine();
        int newLine = hunk.newStartLine();
        for (String line : content.split("\n", -1)) {
            if (line.isEmpty()) {
                throw new IllegalArgumentException("Hunk content has an invalid line prefix");
            }
            switch (line.charAt(0)) {
                case ' ' -> {
                    retainedLines.add(oldLine++);
                    retainedLines.add(newLine++);
                }
                case '-' -> retainedLines.add(oldLine++);
                case '+' -> retainedLines.add(newLine++);
                default -> throw new IllegalArgumentException("Hunk content has an invalid line prefix");
            }
        }
        return ranges(retainedLines);
    }

    private List<SourceLineRange> ranges(TreeSet<Integer> lines) {
        List<SourceLineRange> ranges = new ArrayList<>();
        Integer start = null;
        Integer previous = null;
        for (int line : lines) {
            if (start == null) {
                start = line;
            } else if (line != previous + 1) {
                ranges.add(new SourceLineRange(start, previous));
                start = line;
            }
            previous = line;
        }
        if (start != null) {
            ranges.add(new SourceLineRange(start, previous));
        }
        return List.copyOf(ranges);
    }

    private List<SourceLineRange> mergeRanges(List<SourceLineRange> sourceRanges) {
        TreeSet<Integer> lines = new TreeSet<>();
        for (SourceLineRange range : sourceRanges) {
            for (int line = range.startLine(); line <= range.endLine(); line++) {
                lines.add(line);
            }
        }
        return ranges(lines);
    }

    private int includedLineCount(List<SourceLineRange> ranges) {
        return ranges.stream().mapToInt(range -> range.endLine() - range.startLine() + 1).sum();
    }

    private String range(int start, int count) {
        return count == 0 ? "0" : start + "-" + (start + count - 1);
    }

    private Map<Path, StagedChangeFile> declaredFiles(List<StagedChangeFile> files) {
        Map<Path, StagedChangeFile> byPath = new HashMap<>();
        for (StagedChangeFile file : files) {
            byPath.put(file.path(), file);
        }
        return byPath;
    }

    private boolean containsExcludedDirectory(Path path) {
        for (Path component : path) {
            if (filter.isExcludedDirectory(component)) {
                return true;
            }
        }
        return false;
    }

    private String canonicalDiffMetadata(List<StagedChangeFile> files) {
        StringBuilder metadata = new StringBuilder();
        files.stream().sorted(Comparator.comparing(file -> portable(file.path()))).forEach(file -> {
            metadata.append("change:\n");
            metadata.append("path: ").append(portable(file.path())).append('\n');
            file.previousPath().ifPresent(previous -> metadata.append("previousPath: ")
                    .append(portable(previous)).append('\n'));
            metadata.append("status: ").append(file.status()).append('\n');
            metadata.append("addedLines: ").append(file.addedLines()).append('\n');
            metadata.append("deletedLines: ").append(file.deletedLines()).append('\n');
        });
        return metadata.toString();
    }

    private String header(CapturedStagedChange captured, String projectName) {
        return "STAGED_CHANGE\n"
                + "repository: " + projectName + "\n"
                + "baseCommit: " + captured.change().baseCommit() + "\n"
                + "indexIdentity: " + captured.change().indexIdentity() + "\n"
                + "diffFingerprint: " + captured.change().diffFingerprint() + "\n"
                + "changedFiles: " + captured.change().files().size() + "\n"
                + "CANONICAL_DIFF:\n";
    }

    String appendDiffPrefix(StringBuilder prompt, String canonicalDiff) {
        int remaining = config.maximumSnapshotBytes() - budget.utf8Bytes(prompt.toString());
        int limit = Math.min(Math.max(0, config.maximumSnapshotBytes() / 4), MAXIMUM_DIFF_PREFIX_BYTES);
        String prefix = budget.prefix(canonicalDiff, Math.min(Math.max(0, remaining - 1), limit));
        prompt.append(prefix).append('\n');
        return prefix;
    }

    private int redactionMarkers(String content) {
        int count = 0;
        int offset = 0;
        while ((offset = content.indexOf("[REDACTED]", offset)) >= 0) {
            count++;
            offset += "[REDACTED]".length();
        }
        return count;
    }


    private String repositoryName(Path root) {
        Path fileName = root.getFileName();
        return fileName == null ? "repository" : fileName.toString();
    }

    private String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record FittedHunk(String content, boolean truncated, List<SourceLineRange> evidenceRanges) {
    }
}
