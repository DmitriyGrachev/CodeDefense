package dev.codedefense.domain;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

public record PassportFileReceipt(
        String path,
        String previousPath,
        String status,
        int addedLines,
        int deletedLines) {
    public PassportFileReceipt {
        path = requireRelativePath(path, "path");
        if (previousPath != null) {
            previousPath = requireRelativePath(previousPath, "previousPath");
        }
        status = requireNonBlank(status, "status");
        try {
            StagedFileStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status is unsupported", exception);
        }
        if (addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException("line counts cannot be negative");
        }
    }

    static PassportFileReceipt from(StagedChangeFile file) {
        return new PassportFileReceipt(portable(file.path()),
                file.previousPath().map(PassportFileReceipt::portable).orElse(null),
                file.status().name(), file.addedLines(), file.deletedLines());
    }

    private static String requireRelativePath(String value, String field) {
        value = requireNonBlank(value, field).replace('\\', '/');
        try {
            Path path = Path.of(value);
            if (path.isAbsolute() || !path.equals(path.normalize()) || path.getNameCount() == 0
                    || value.matches("^[A-Za-z]:/.*")) {
                throw new IllegalArgumentException(field + " must be a normalized relative path");
            }
            for (Path segment : path) {
                String text = segment.toString();
                if (text.equals(".") || text.equals("..") || text.chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException(field + " contains an unsafe segment");
                }
            }
            return value;
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(field + " has an invalid path", exception);
        }
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank");
        }
        return value.strip();
    }
}
