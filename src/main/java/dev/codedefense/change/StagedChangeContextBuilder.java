package dev.codedefense.change;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.scanner.FilePrioritizer;
import dev.codedefense.scanner.LineNumberFormatter;
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
import java.util.Optional;

/** Builds a bounded project snapshot exclusively from captured Git index blobs. */
public final class StagedChangeContextBuilder {
    private static final int MAXIMUM_DIFF_PREFIX_BYTES = 16 * 1024;

    private final CodeDefenseConfig config;
    private final ProjectFileFilter filter;
    private final FilePrioritizer prioritizer;
    private final SecretRedactor redactor;
    private final LineNumberFormatter formatter;
    private final SnapshotBudget budget;

    public StagedChangeContextBuilder() {
        this(CodeDefenseConfig.defaults());
    }

    StagedChangeContextBuilder(CodeDefenseConfig config) {
        this(config, new ProjectFileFilter(), new FilePrioritizer(), new SecretRedactor(),
                new LineNumberFormatter(), new SnapshotBudget());
    }

    StagedChangeContextBuilder(CodeDefenseConfig config, ProjectFileFilter filter,
            FilePrioritizer prioritizer, SecretRedactor redactor, LineNumberFormatter formatter,
            SnapshotBudget budget) {
        this.config = config;
        this.filter = filter;
        this.prioritizer = prioritizer;
        this.redactor = redactor;
        this.formatter = formatter;
        this.budget = budget;
    }

    public ProjectSnapshot build(CapturedStagedChange captured) {
        Map<Path, StagedChangeFile> declaredFiles = declaredFiles(captured.change().files());
        Map<Path, IndexBlob> byPath = blobsByPath(captured.blobs(), declaredFiles);
        List<SourceFile> candidates = currentCandidates(byPath);
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
            IndexBlob blob = byPath.get(candidate.relativePath());
            int remaining = config.maximumSnapshotBytes() - budget.utf8Bytes(prompt.toString());
            int unitLimit = Math.min(remaining, config.maximumFileBlockBytes());
            if (unitLimit <= 1) {
                break;
            }
            SecretRedactor.RedactionResult indexed = redactor.redact(blob.indexContent().orElseThrow());
            FittedBlock indexBlock = fitBlock("INDEX_FILE", blob.file(), indexed.content(),
                    blob.indexTruncated(), unitLimit - 1);
            if (indexBlock == null) {
                continue;
            }

            String unit = indexBlock.content();
            boolean truncated = indexBlock.truncated();
            if (needsBaseContext(blob) && blob.baseContent().filter(content -> !content.isBlank()).isPresent()) {
                SecretRedactor.RedactionResult base = redactor.redact(blob.baseContent().orElseThrow());
                int availableForBase = unitLimit - budget.utf8Bytes(unit + "\n") - 1;
                FittedBlock baseBlock = fitBlock("HEAD_FILE", blob.file(), base.content(), blob.baseTruncated(),
                        availableForBase);
                if (baseBlock != null) {
                    unit += "\n" + baseBlock.content();
                    truncated |= baseBlock.truncated();
                }
            }
            if (budget.utf8Bytes(unit + "\n") > unitLimit) {
                continue;
            }
            prompt.append(unit).append('\n');
            redactions += redactionMarkers(unit);
            selected.add(new ProjectSnapshot.SelectedFile(candidate.relativePath(), indexBlock.lineCount(), truncated,
                    budget.utf8Bytes(unit + "\n")));
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

    private Map<Path, StagedChangeFile> declaredFiles(List<StagedChangeFile> files) {
        Map<Path, StagedChangeFile> byPath = new HashMap<>();
        for (StagedChangeFile file : files) {
            byPath.put(file.path(), file);
        }
        return byPath;
    }

    private Map<Path, IndexBlob> blobsByPath(List<IndexBlob> blobs, Map<Path, StagedChangeFile> declaredFiles) {
        Map<Path, IndexBlob> byPath = new HashMap<>();
        for (IndexBlob blob : blobs) {
            StagedChangeFile declared = declaredFiles.get(blob.file().path());
            if (!blob.file().equals(declared) || blob.file().status() == StagedFileStatus.DELETED) {
                continue;
            }
            if (byPath.put(blob.file().path(), blob) != null) {
                throw new IllegalArgumentException("Captured change contains duplicate paths");
            }
        }
        return byPath;
    }

    private List<SourceFile> currentCandidates(Map<Path, IndexBlob> byPath) {
        return byPath.values().stream()
                .filter(blob -> blob.indexContent().filter(content -> !content.isBlank()).isPresent())
                .filter(blob -> isCurrent(blob.file()))
                .filter(blob -> !filter.isExcludedFile(blob.file().path()))
                .filter(blob -> !containsExcludedDirectory(blob.file().path()))
                .filter(blob -> filter.isSupportedFile(blob.file().path()))
                .map(blob -> new SourceFile(blob.file().path(),
                        blob.indexContent().orElseThrow().getBytes(StandardCharsets.UTF_8).length))
                .sorted(Comparator.comparing(source -> portable(source.relativePath())))
                .toList();
    }

    private boolean isCurrent(StagedChangeFile file) {
        return file.status() == StagedFileStatus.ADDED || file.status() == StagedFileStatus.MODIFIED
                || file.status() == StagedFileStatus.RENAMED;
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
            metadata.append("diff --staged ").append(portable(file.path())).append('\n');
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
                + "indexTree: " + captured.change().indexTree() + "\n"
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

    private boolean needsBaseContext(IndexBlob blob) {
        return blob.file().status() == StagedFileStatus.MODIFIED || blob.file().status() == StagedFileStatus.RENAMED;
    }

    private FittedBlock fitBlock(String kind, StagedChangeFile file, String content, boolean sourceTruncated,
            int byteLimit) {
        if (byteLimit <= 0 || content.isBlank()) {
            return null;
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        while (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        String full = renderBlock(kind, file, normalized, sourceTruncated);
        if (budget.utf8Bytes(full) <= byteLimit) {
            return new FittedBlock(full, sourceTruncated, lineCount(normalized));
        }

        StringBuilder included = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            String candidate = included.isEmpty() ? line : included + "\n" + line;
            if (budget.utf8Bytes(renderBlock(kind, file, candidate, true)) <= byteLimit) {
                included.setLength(0);
                included.append(candidate);
                continue;
            }
            String prefix = longestLinePrefix(kind, file, included.toString(), line, byteLimit);
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
        String rendered = renderBlock(kind, file, included.toString(), true);
        return new FittedBlock(rendered, true, lineCount(included.toString()));
    }

    private String longestLinePrefix(String kind, StagedChangeFile file, String included, String line, int byteLimit) {
        StringBuilder prefix = new StringBuilder();
        for (int offset = 0; offset < line.length();) {
            int codePoint = line.codePointAt(offset);
            prefix.appendCodePoint(codePoint);
            String candidate = included.isEmpty() ? prefix.toString() : included + "\n" + prefix;
            if (budget.utf8Bytes(renderBlock(kind, file, candidate, true)) > byteLimit) {
                prefix.setLength(prefix.length() - Character.charCount(codePoint));
                break;
            }
            offset += Character.charCount(codePoint);
        }
        return prefix.toString();
    }

    private String renderBlock(String kind, StagedChangeFile file, String content, boolean truncated) {
        String numbered = formatter.format(content);
        return kind + ": " + portable(file.path()) + "\n"
                + "STATUS: " + file.status() + "\n"
                + "LANGUAGE: " + language(file.path()) + "\n"
                + "INCLUDED_LINES: " + lineCount(content) + "\n"
                + "TRUNCATED: " + truncated + "\n"
                + numbered;
    }

    private int lineCount(String content) {
        return (int) formatter.format(content).lines().filter(line -> line.matches("\\d+ \\|.*")).count();
    }

    private String language(Path path) {
        String value = portable(path);
        int dot = value.lastIndexOf('.');
        return dot < 0 ? "text" : value.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private String repositoryName(Path root) {
        Path fileName = root.getFileName();
        return fileName == null ? "repository" : fileName.toString();
    }

    private String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record FittedBlock(String content, boolean truncated, int lineCount) {
    }
}
