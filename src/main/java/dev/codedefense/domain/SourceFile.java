package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.Objects;

public record SourceFile(Path relativePath) {
    public SourceFile {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("Source file path must be relative");
        }
    }
}
