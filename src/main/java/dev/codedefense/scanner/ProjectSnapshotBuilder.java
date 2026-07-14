package dev.codedefense.scanner;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import java.util.ArrayList;
import java.util.List;

public final class ProjectSnapshotBuilder {
    private final CodeDefenseConfig config; private final FilePrioritizer prioritizer; private final ProjectTypeDetector detector;
    private final SecretRedactor redactor; private final LineNumberFormatter formatter; private final SnapshotBudget budget; private final BoundedTextFileReader reader;
    public ProjectSnapshotBuilder(CodeDefenseConfig config) { this(config, new FilePrioritizer(), new ProjectTypeDetector(), new SecretRedactor(), new LineNumberFormatter(), new SnapshotBudget(), new BoundedTextFileReader()); }
    ProjectSnapshotBuilder(CodeDefenseConfig config, FilePrioritizer prioritizer, ProjectTypeDetector detector, SecretRedactor redactor, LineNumberFormatter formatter, SnapshotBudget budget, BoundedTextFileReader reader) {
        this.config=config; this.prioritizer=prioritizer; this.detector=detector; this.redactor=redactor; this.formatter=formatter; this.budget=budget; this.reader=reader;
    }
    public ProjectSnapshot build(ScanSummary summary) {
        String name = summary.root().getFileName() == null ? "project" : summary.root().getFileName().toString();
        String type = detector.detect(summary); StringBuilder prompt = new StringBuilder("PROJECT\nname: ").append(name).append("\ndetectedType: ").append(type).append("\n\n");
        List<ProjectSnapshot.SelectedFile> selected = new ArrayList<>(); int redactions = 0;
        for (SourceFile source : prioritizer.prioritize(summary.candidates())) {
            if (selected.size() == config.maximumSelectedFiles()) break;
            int remaining = config.maximumSnapshotBytes() - budget.utf8Bytes(prompt.toString()); if (remaining <= 0) break;
            var read = reader.read(summary.root(), summary.root().resolve(source.relativePath()), config.maximumFileBlockBytes());
            if (!read.available()) continue;
            String raw = read.content();
            SecretRedactor.RedactionResult result = redactor.redact(raw);
            String numbered = formatter.format(result.content()); String path = source.relativePath().toString().replace('\\', '/');
            String block = block(path, language(path), numbered, false); int limit = Math.min(remaining, config.maximumFileBlockBytes());
            boolean truncated = budget.utf8Bytes(block + "\n") > limit || read.truncated();
            if (truncated) block = fit(path, language(path), numbered, limit);
            if (block == null || budget.utf8Bytes(block) == 0) continue;
            int lines = (int) block.lines().filter(line -> line.matches("\\d+ \\|.*")).count();
            if (lines == 0) continue;
            block = block(path, language(path), contentOnly(block), truncated);
            if (budget.utf8Bytes(block + "\n") > limit) continue;
            prompt.append(block).append('\n'); redactions += result.replacementCount(); selected.add(new ProjectSnapshot.SelectedFile(source.relativePath(), lines, truncated, budget.utf8Bytes(block + "\n")));
        }
        String content = prompt.toString(); if (selected.isEmpty()) throw new EmptyProjectSnapshotException(); if (budget.utf8Bytes(content) > config.maximumSnapshotBytes()) throw new IllegalStateException("Snapshot exceeds configured byte limit"); return new ProjectSnapshot(summary.root(), name, type, summary, selected, content, budget.utf8Bytes(content), redactions);
    }
    public CodeDefenseConfig config() { return config; }
    private String fit(String path, String language, String numbered, int limit) {
        int blockLimit = limit - 1;
        String candidate = block(path, language, "", true); int header = budget.utf8Bytes(candidate); if (header >= blockLimit) return null;
        StringBuilder included = new StringBuilder(); for (String line : numbered.split("\n", -1)) { String next = included + line + "\n"; if (budget.utf8Bytes(block(path, language, next, true)) > blockLimit) { String prefix=budget.prefix(line + "\n", Math.max(0, blockLimit - budget.utf8Bytes(block(path,language,included.toString(),true)))); if (!prefix.isBlank()) included.append(prefix); break; } included.append(line).append('\n'); }
        return block(path, language, included.toString(), true);
    }
    private String contentOnly(String block) { int index = block.indexOf("\n", block.indexOf("TRUNCATED:")); return index < 0 ? "" : block.substring(index + 1); }
    private String block(String path, String language, String content, boolean truncated) { int lines=(int)content.lines().filter(line -> line.matches("\\d+ \\|.*")).count(); return "FILE: " + path + "\nLANGUAGE: " + language + "\nINCLUDED_LINES: " + lines + "\nTRUNCATED: " + truncated + "\n" + content; }
    private String language(String path) { int dot=path.lastIndexOf('.'); return dot<0 ? "text" : path.substring(dot+1).toLowerCase(); }
}
