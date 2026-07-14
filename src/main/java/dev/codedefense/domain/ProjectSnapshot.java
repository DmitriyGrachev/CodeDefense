package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.List;

public record ProjectSnapshot(Path root, String projectName, String projectType, ScanSummary scanSummary,
                              List<SelectedFile> selectedFiles, String promptContent, int promptBytes, int redactionCount) {
    public ProjectSnapshot { selectedFiles = List.copyOf(selectedFiles); }
    public record SelectedFile(Path relativePath, int includedLines, boolean truncated, int renderedBytes) {}
}
