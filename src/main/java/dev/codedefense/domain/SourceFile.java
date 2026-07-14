package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.Objects;

public record SourceFile(Path relativePath, long sizeBytes) {
    public SourceFile(Path relativePath) {
        this(relativePath, 0);
    }

    public SourceFile {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("Source file path must be relative");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("Source file size cannot be negative");
        }
    }
}
