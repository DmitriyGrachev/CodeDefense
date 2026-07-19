package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record StagedChangeFile(
        Path path,
        Optional<Path> previousPath,
        StagedFileStatus status,
        int addedLines,
        int deletedLines) {
    public StagedChangeFile {
        validatePath(path, "path");
        Objects.requireNonNull(previousPath, "previousPath");
        Objects.requireNonNull(status, "status");
        if ((status == StagedFileStatus.RENAMED) != previousPath.isPresent()) {
            throw new IllegalArgumentException("previousPath is required only for renamed files");
        }
        if (previousPath.isPresent()) {
            validatePath(previousPath.orElseThrow(), "previousPath");
            if (previousPath.orElseThrow().equals(path)) {
                throw new IllegalArgumentException("renamed paths must differ");
            }
        }
        if (addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException("line counts cannot be negative");
        }
    }

    public StagedChangeFile(Path path, StagedFileStatus status, int addedLines, int deletedLines) {
        this(path, Optional.empty(), status, addedLines, deletedLines);
    }

    private static void validatePath(Path path, String field) {
        Objects.requireNonNull(path, field);
        if (path.isAbsolute() || !path.equals(path.normalize()) || path.getNameCount() == 0) {
            throw new IllegalArgumentException(field + " must be a normalized relative path");
        }
        for (Path segment : path) {
            String value = segment.toString();
            if (value.isEmpty() || value.equals(".") || value.equals("..") || containsControlCharacter(value)) {
                throw new IllegalArgumentException(field + " contains an unsafe segment");
            }
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    @Override
    public String toString() {
        return "StagedChangeFile[path=%s, previousPath=%s, status=%s, addedLines=%d, deletedLines=%d]"
                .formatted(path, previousPath.orElse(null), status, addedLines, deletedLines);
    }
}
