package dev.codedefense.domain;

import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record StagedPassportGateResult(
        int protocolVersion,
        StagedPassportGateState state,
        StagedPassportGateReason reason,
        String diffFingerprint,
        int attemptNumber,
        int stagedFileCount,
        int addedLines,
        int deletedLines,
        List<String> relativePaths) {
    private static final String SHA_256_PATTERN = "[0-9a-f]{64}";

    public StagedPassportGateResult {
        if (protocolVersion != 1) throw new IllegalArgumentException("protocolVersion must be 1");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        diffFingerprint = Objects.requireNonNull(diffFingerprint, "diffFingerprint");
        if (!validReason(state, reason)) throw new IllegalArgumentException("reason does not match state");
        if (stagedFileCount < 0 || addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException("staged counts cannot be negative");
        }

        boolean hasStagedMetadata = state == StagedPassportGateState.UNDEFENDED
                || state == StagedPassportGateState.CURRENT
                || state == StagedPassportGateState.EXPIRED;
        if (hasStagedMetadata) {
            if (!diffFingerprint.matches(SHA_256_PATTERN)) {
                throw new IllegalArgumentException("diffFingerprint has an invalid format");
            }
            if (stagedFileCount == 0) throw new IllegalArgumentException("stagedFileCount must be positive");
        } else if (!diffFingerprint.isEmpty() || stagedFileCount != 0 || addedLines != 0 || deletedLines != 0) {
            throw new IllegalArgumentException("source-free states cannot contain staged metadata");
        }

        if (state == StagedPassportGateState.CURRENT ? attemptNumber < 1 : attemptNumber != 0) {
            throw new IllegalArgumentException("attemptNumber does not match state");
        }

        relativePaths = normalizedPaths(relativePaths);
        if (relativePaths.size() > 30) throw new IllegalArgumentException("relativePaths cannot exceed 30 entries");
        if (state != StagedPassportGateState.EXPIRED && !relativePaths.isEmpty()) {
            throw new IllegalArgumentException("relativePaths are available only for expired staged changes");
        }
    }

    private static boolean validReason(StagedPassportGateState state, StagedPassportGateReason reason) {
        return switch (state) {
            case NO_STAGED_CHANGE -> reason == StagedPassportGateReason.NO_INDEX_ENTRIES;
            case UNDEFENDED -> reason == StagedPassportGateReason.NO_STAGED_HISTORY;
            case CURRENT -> reason == StagedPassportGateReason.IDENTITY_MATCH;
            case EXPIRED -> reason == StagedPassportGateReason.IDENTITY_CHANGED;
            case UNAVAILABLE -> reason == StagedPassportGateReason.INVALID_REPOSITORY
                    || reason == StagedPassportGateReason.GIT_CAPTURE_FAILED
                    || reason == StagedPassportGateReason.PASSPORT_STORE_FAILED;
        };
    }

    private static List<String> normalizedPaths(List<String> values) {
        Objects.requireNonNull(values, "relativePaths");
        java.util.ArrayList<String> paths = new java.util.ArrayList<>();
        for (String value : values) {
            Objects.requireNonNull(value, "relativePath");
            String portable = value.replace('\\', '/');
            Path path;
            try {
                path = Path.of(portable).normalize();
            } catch (InvalidPathException exception) {
                throw new IllegalArgumentException("relativePath is unsafe");
            }
            if (portable.isBlank() || portable.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("relativePath is unsafe");
            }
            if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")
                    || portable.matches("^[A-Za-z]:.*")) {
                throw new IllegalArgumentException("relativePath must stay within the repository");
            }
            paths.add(path.toString().replace('\\', '/'));
        }
        paths.sort(String::compareTo);
        if (new HashSet<>(paths).size() != paths.size()) {
            throw new IllegalArgumentException("relativePaths must be unique");
        }
        return List.copyOf(paths);
    }

    @Override
    public String toString() {
        return ("StagedPassportGateResult[protocolVersion=%d, state=%s, reason=%s, attemptNumber=%d, "
                + "stagedFileCount=%d, addedLines=%d, deletedLines=%d, relativePathCount=%d]")
                .formatted(protocolVersion, state, reason, attemptNumber, stagedFileCount,
                        addedLines, deletedLines, relativePaths.size());
    }
}
