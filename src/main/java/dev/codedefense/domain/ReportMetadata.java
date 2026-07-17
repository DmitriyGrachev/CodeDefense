package dev.codedefense.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;

public record ReportMetadata(Instant analyzedAt, String model, String projectName, String projectType,
                             List<AnalyzedFile> selectedFiles, int snapshotBytes, int redactionCount) {
    public ReportMetadata {
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        model = CodeEvidence.requireNonBlank(model, "model");
        projectName = CodeEvidence.requireNonBlank(projectName, "projectName");
        projectType = CodeEvidence.requireNonBlank(projectType, "projectType");
        Objects.requireNonNull(selectedFiles, "selectedFiles");
        selectedFiles = List.copyOf(selectedFiles);
        if (selectedFiles.isEmpty() || selectedFiles.stream().anyMatch(Objects::isNull) || snapshotBytes <= 0 || redactionCount < 0
                || selectedFiles.stream().map(AnalyzedFile::path).collect(java.util.stream.Collectors.toCollection(HashSet::new)).size() != selectedFiles.size()) {
            throw new IllegalArgumentException("Report metadata is invalid");
        }
    }

    public static ReportMetadata from(ProjectSnapshot snapshot, String model, Instant analyzedAt) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<AnalyzedFile> files = snapshot.selectedFiles().stream()
                .map(file -> new AnalyzedFile(file.relativePath().toString(), file.includedLines(), file.truncated(), file.renderedBytes()))
                .toList();
        return new ReportMetadata(analyzedAt, model, snapshot.projectName(), snapshot.projectType(), files,
                snapshot.promptBytes(), snapshot.redactionCount());
    }

    @Override
    public String toString() {
        return "ReportMetadata[analyzedAt=%s, model=%s, projectName=%s, projectType=%s, selectedFileCount=%d, snapshotBytes=%d, redactionCount=%d]"
                .formatted(analyzedAt, model, projectName, projectType, selectedFiles.size(), snapshotBytes, redactionCount);
    }
}
