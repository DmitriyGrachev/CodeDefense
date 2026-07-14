package dev.codedefense.scanner;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class ProjectSnapshotBuilder {
    private final CodeDefenseConfig config; private final FilePrioritizer prioritizer; private final ProjectTypeDetector detector;
    private final SecretRedactor redactor; private final LineNumberFormatter formatter; private final SnapshotBudget budget;
    public ProjectSnapshotBuilder(CodeDefenseConfig config) { this(config, new FilePrioritizer(), new ProjectTypeDetector(), new SecretRedactor(), new LineNumberFormatter(), new SnapshotBudget()); }
    ProjectSnapshotBuilder(CodeDefenseConfig config, FilePrioritizer prioritizer, ProjectTypeDetector detector, SecretRedactor redactor, LineNumberFormatter formatter, SnapshotBudget budget) {
        this.config=config; this.prioritizer=prioritizer; this.detector=detector; this.redactor=redactor; this.formatter=formatter; this.budget=budget;
    }
    public ProjectSnapshot build(ScanSummary summary) {
        String name = summary.root().getFileName() == null ? "project" : summary.root().getFileName().toString();
        String type = detector.detect(summary); StringBuilder prompt = new StringBuilder("PROJECT\nname: ").append(name).append("\ndetectedType: ").append(type).append("\n\n");
        List<ProjectSnapshot.SelectedFile> selected = new ArrayList<>(); int redactions = 0;
        for (SourceFile source : prioritizer.prioritize(summary.candidates())) {
            if (selected.size() == config.maximumSelectedFiles()) break;
            int remaining = config.maximumSnapshotBytes() - budget.utf8Bytes(prompt.toString()); if (remaining <= 0) break;
            String raw = readPrefix(summary.root().resolve(source.relativePath()), config.maximumFileBlockBytes());
            SecretRedactor.RedactionResult result = redactor.redact(raw); redactions += result.replacementCount();
            String numbered = formatter.format(result.content()); String path = source.relativePath().toString().replace('\\', '/');
            String block = block(path, language(path), numbered, false); int limit = Math.min(remaining, config.maximumFileBlockBytes());
            boolean truncated = budget.utf8Bytes(block) > limit || source.sizeBytes() > raw.getBytes(StandardCharsets.UTF_8).length;
            if (truncated) block = fit(path, language(path), numbered, limit);
            if (block == null || budget.utf8Bytes(block) == 0) continue;
            int lines = (int) block.lines().filter(line -> line.matches("\\d+ \\|.*")).count();
            if (lines == 0) continue;
            block = block(path, language(path), contentOnly(block), truncated);
            if (budget.utf8Bytes(block) > limit) continue;
            prompt.append(block).append('\n'); selected.add(new ProjectSnapshot.SelectedFile(source.relativePath(), lines, truncated, budget.utf8Bytes(block)));
        }
        String content = prompt.toString(); return new ProjectSnapshot(summary.root(), name, type, summary, selected, content, budget.utf8Bytes(content), redactions);
    }
    private String fit(String path, String language, String numbered, int limit) {
        String candidate = block(path, language, "", true); int header = budget.utf8Bytes(candidate); if (header >= limit) return null;
        StringBuilder included = new StringBuilder(); for (String line : numbered.split("\n", -1)) { String next = included + line + "\n"; if (budget.utf8Bytes(block(path, language, next, true)) > limit) { String prefix=budget.prefix(line + "\n", Math.max(0, limit - budget.utf8Bytes(block(path,language,included.toString(),true)))); if (!prefix.isBlank()) included.append(prefix); break; } included.append(line).append('\n'); }
        return block(path, language, included.toString(), true);
    }
    private String contentOnly(String block) { int index = block.indexOf("\n", block.indexOf("TRUNCATED:")); return index < 0 ? "" : block.substring(index + 1); }
    private String block(String path, String language, String content, boolean truncated) { int lines=(int)content.lines().filter(line -> line.matches("\\d+ \\|.*")).count(); return "FILE: " + path + "\nLANGUAGE: " + language + "\nINCLUDED_LINES: " + lines + "\nTRUNCATED: " + truncated + "\n" + content; }
    private String language(String path) { int dot=path.lastIndexOf('.'); return dot<0 ? "text" : path.substring(dot+1).toLowerCase(); }
    private String readPrefix(java.nio.file.Path path, int limit) { try (var in=Files.newInputStream(path)) { return new String(in.readNBytes(limit), StandardCharsets.UTF_8); } catch (IOException ignored) { return ""; } }
}
