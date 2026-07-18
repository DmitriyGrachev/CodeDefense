package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.Objects;

public record StagedChangeFile(Path path, StagedFileStatus status, int addedLines, int deletedLines) {
    public StagedChangeFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(status, "status");
        if (path.isAbsolute() || !path.equals(path.normalize()) || path.getNameCount() == 0) {
            throw new IllegalArgumentException("path must be a normalized relative path");
        }
        for (Path segment : path) {
            String value = segment.toString();
            if (value.isEmpty() || value.equals(".") || value.equals("..") || containsControlCharacter(value)) {
                throw new IllegalArgumentException("path contains an unsafe segment");
            }
        }
        if (addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException("line counts cannot be negative");
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    @Override
    public String toString() {
        return "StagedChangeFile[path=%s, status=%s, addedLines=%d, deletedLines=%d]"
                .formatted(path, status, addedLines, deletedLines);
    }
}
