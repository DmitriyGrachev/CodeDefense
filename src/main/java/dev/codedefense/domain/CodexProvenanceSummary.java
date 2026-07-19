package dev.codedefense.domain;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CodexProvenanceSummary(
        int schemaVersion,
        CodexProvenanceStatus status,
        String threadIdentityHash,
        String codexVersion,
        int selectedFileCount,
        int matchedFileCount,
        List<String> matchedRelativePaths,
        Instant capturedAt) {
    public CodexProvenanceSummary {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("unsupported provenance schema version");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(threadIdentityHash, "threadIdentityHash");
        Objects.requireNonNull(codexVersion, "codexVersion");
        Objects.requireNonNull(matchedRelativePaths, "matchedRelativePaths");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (selectedFileCount < 1 || matchedFileCount < 0 || matchedFileCount > selectedFileCount) {
            throw new IllegalArgumentException("provenance counts are inconsistent");
        }
        matchedRelativePaths = validatePaths(matchedRelativePaths);
        if (matchedRelativePaths.size() != matchedFileCount) {
            throw new IllegalArgumentException("matched paths must equal matched file count");
        }
        if (status == CodexProvenanceStatus.UNAVAILABLE) {
            if (!threadIdentityHash.isEmpty() || !codexVersion.isEmpty()
                    || matchedFileCount != 0 || !matchedRelativePaths.isEmpty()) {
                throw new IllegalArgumentException("unavailable provenance must not retain identity or paths");
            }
        } else {
            if (!threadIdentityHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("thread identity hash has an invalid format");
            }
            if (!codexVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?")
                    || codexVersion.length() > 64) {
                throw new IllegalArgumentException("Codex version has an invalid format");
            }
        }
        switch (status) {
            case EXACT_CHANGE_MATCH -> {
                if (matchedFileCount != selectedFileCount) {
                    throw new IllegalArgumentException("exact provenance must match every selected file");
                }
            }
            case PARTIAL_PATH_MATCH -> {
                if (matchedFileCount == 0 || matchedFileCount >= selectedFileCount) {
                    throw new IllegalArgumentException("partial provenance requires a strict subset");
                }
            }
            case NO_MATCH, UNAVAILABLE -> {
                if (matchedFileCount != 0) {
                    throw new IllegalArgumentException("this provenance status cannot retain matches");
                }
            }
        }
    }

    private static List<String> validatePaths(List<String> values) {
        List<String> copy = values.stream().map(value -> requireSafeRelativePath(value)).sorted().toList();
        Set<String> unique = new HashSet<>(copy);
        if (unique.size() != copy.size()) {
            throw new IllegalArgumentException("matched paths must be unique");
        }
        return List.copyOf(copy);
    }

    private static String requireSafeRelativePath(String value) {
        Objects.requireNonNull(value, "matched path");
        String normalized = value.replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.indexOf('\0') >= 0
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("matched path is unsafe");
        }
        try {
            Path path = Path.of(normalized);
            if (path.isAbsolute() || path.normalize().startsWith("..") || normalized.matches("^[A-Za-z]:/.*")) {
                throw new IllegalArgumentException("matched path is unsafe");
            }
            return path.normalize().toString().replace('\\', '/');
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("matched path is unsafe", exception);
        }
    }

    @Override
    public String toString() {
        return "CodexProvenanceSummary[status=%s, selectedFileCount=%d, matchedFileCount=%d]"
                .formatted(status, selectedFileCount, matchedFileCount);
    }
}
