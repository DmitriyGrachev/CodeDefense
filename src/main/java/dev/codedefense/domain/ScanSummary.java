package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ScanSummary(Path root, int discoveredFileCount, int ignoredFileCount, List<SourceFile> candidates) {
    public ScanSummary {
        Objects.requireNonNull(root, "root");
        candidates = List.copyOf(candidates);
        if (discoveredFileCount < 0 || ignoredFileCount < 0) {
            throw new IllegalArgumentException("Scan counts cannot be negative");
        }
        if (discoveredFileCount != ignoredFileCount + candidates.size()) {
            throw new IllegalArgumentException("Discovered files must equal ignored files plus accepted candidates");
        }
    }

    public int acceptedCandidateCount() {
        return candidates.size();
    }
}
