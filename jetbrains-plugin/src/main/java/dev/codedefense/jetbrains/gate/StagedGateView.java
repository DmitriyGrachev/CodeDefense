package dev.codedefense.jetbrains.gate;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Validated, source-free staged Passport gate state returned by the bundled CLI. */
public record StagedGateView(
        int protocolVersion,
        State state,
        Reason reason,
        String diffFingerprint,
        int attemptNumber,
        int stagedFileCount,
        int addedLines,
        int deletedLines,
        List<String> relativePaths) {
    private static final String SHA_256_PATTERN = "[0-9a-f]{64}";

    public StagedGateView {
        if (protocolVersion != 1) throw invalid();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        diffFingerprint = Objects.requireNonNull(diffFingerprint, "diffFingerprint");
        if (!validReason(state, reason)) throw invalid();
        if (stagedFileCount < 0 || addedLines < 0 || deletedLines < 0) throw invalid();

        boolean hasStagedMetadata = state == State.UNDEFENDED || state == State.CURRENT || state == State.EXPIRED;
        if (hasStagedMetadata) {
            if (!diffFingerprint.matches(SHA_256_PATTERN) || stagedFileCount == 0) throw invalid();
        } else if (!diffFingerprint.isEmpty() || stagedFileCount != 0 || addedLines != 0 || deletedLines != 0) {
            throw invalid();
        }
        if (state == State.CURRENT ? attemptNumber < 1 : attemptNumber != 0) throw invalid();

        relativePaths = normalizePaths(relativePaths);
        if (relativePaths.size() > 30 || state != State.EXPIRED && !relativePaths.isEmpty()) throw invalid();
    }

    public String shortFingerprint() {
        return diffFingerprint.isEmpty() ? "" : diffFingerprint.substring(0, 12);
    }

    @Override
    public String toString() {
        return ("StagedGateView[protocolVersion=%d, state=%s, reason=%s, attemptNumber=%d, "
                + "stagedFileCount=%d, addedLines=%d, deletedLines=%d, relativePathCount=%d]")
                .formatted(protocolVersion, state, reason, attemptNumber, stagedFileCount,
                        addedLines, deletedLines, relativePaths.size());
    }

    private static boolean validReason(State state, Reason reason) {
        return switch (state) {
            case NO_STAGED_CHANGE -> reason == Reason.NO_INDEX_ENTRIES;
            case UNDEFENDED -> reason == Reason.NO_STAGED_HISTORY;
            case CURRENT -> reason == Reason.IDENTITY_MATCH;
            case EXPIRED -> reason == Reason.IDENTITY_CHANGED;
            case UNAVAILABLE -> reason == Reason.INVALID_REPOSITORY || reason == Reason.GIT_CAPTURE_FAILED
                    || reason == Reason.PASSPORT_STORE_FAILED;
        };
    }

    private static List<String> normalizePaths(List<String> values) {
        Objects.requireNonNull(values, "relativePaths");
        if (values.size() > 30) throw invalid();
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null) throw invalid();
            String portable = value.replace('\\', '/');
            if (portable.isBlank() || portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")
                    || portable.chars().anyMatch(Character::isISOControl)
                    || List.of(portable.split("/", -1)).contains("..")) {
                throw invalid();
            }
            try {
                Path path = Path.of(portable).normalize();
                if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")) throw invalid();
                normalized.add(path.toString().replace('\\', '/'));
            } catch (InvalidPathException exception) {
                throw invalid();
            }
        }
        normalized.sort(String::compareTo);
        if (new HashSet<>(normalized).size() != normalized.size()) throw invalid();
        return List.copyOf(normalized);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Staged gate response is invalid.");
    }

    public enum State {
        NO_STAGED_CHANGE, UNDEFENDED, CURRENT, EXPIRED, UNAVAILABLE
    }

    public enum Reason {
        NONE, NO_INDEX_ENTRIES, NO_STAGED_HISTORY, IDENTITY_MATCH, IDENTITY_CHANGED,
        INVALID_REPOSITORY, GIT_CAPTURE_FAILED, PASSPORT_STORE_FAILED
    }
}
