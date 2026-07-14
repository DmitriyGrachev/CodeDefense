package dev.codedefense.domain;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;

public record ProjectSnapshot(Path root, String projectName, String projectType, ScanSummary scanSummary,
                              List<SelectedFile> selectedFiles, String promptContent, int promptBytes, int redactionCount) {
    public ProjectSnapshot {
        Objects.requireNonNull(root); Objects.requireNonNull(projectName); Objects.requireNonNull(projectType); Objects.requireNonNull(scanSummary); Objects.requireNonNull(promptContent);
        selectedFiles = List.copyOf(selectedFiles);
        if (redactionCount < 0) throw new IllegalArgumentException("Redaction count cannot be negative");
        if (promptBytes != promptContent.getBytes(StandardCharsets.UTF_8).length) throw new IllegalArgumentException("Prompt byte count must match UTF-8 content");
        if (selectedFiles.isEmpty()) throw new IllegalArgumentException("Snapshot contains no selected files");
        var paths = new HashSet<Path>();
        for (SelectedFile file : selectedFiles) if (file.relativePath() == null || file.relativePath().isAbsolute() || file.relativePath().startsWith("..") || !paths.add(file.relativePath()) || file.includedLines() <= 0 || file.renderedBytes() <= 0) throw new IllegalArgumentException("Invalid selected file metadata");
    }
    public record SelectedFile(Path relativePath, int includedLines, boolean truncated, int renderedBytes) {}
}
