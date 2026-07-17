package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.Objects;

public record SavedReport(Path path, NarrativeSource narrativeSource) {
    public SavedReport {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(narrativeSource, "narrativeSource");
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Saved report path must be absolute");
        }
        path = path.normalize();
    }
}
