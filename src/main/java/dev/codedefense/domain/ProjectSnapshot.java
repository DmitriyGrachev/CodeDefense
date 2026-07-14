package dev.codedefense.domain;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

public record ProjectSnapshot(Path root, String projectName, String projectType, ScanSummary scanSummary,
                              List<SelectedFile> selectedFiles, String promptContent, int promptBytes, int redactionCount) {
    public ProjectSnapshot {
        selectedFiles = List.copyOf(selectedFiles);
        if (promptBytes != promptContent.getBytes(StandardCharsets.UTF_8).length) throw new IllegalArgumentException("Prompt byte count must match UTF-8 content");
        if (selectedFiles.isEmpty()) throw new IllegalArgumentException("Snapshot contains no selected files");
        for (SelectedFile file : selectedFiles) if (file.relativePath() == null || file.relativePath().isAbsolute() || file.includedLines() <= 0 || file.renderedBytes() <= 0) throw new IllegalArgumentException("Invalid selected file metadata");
    }
    public record SelectedFile(Path relativePath, int includedLines, boolean truncated, int renderedBytes) {}
}
