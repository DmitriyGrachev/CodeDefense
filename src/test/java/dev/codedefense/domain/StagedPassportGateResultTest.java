package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StagedPassportGateResultTest {
    private static final String FINGERPRINT = "d".repeat(64);

    @Test
    void normalizesExpiredPathsToAnImmutableSortedUniqueList() {
        ArrayList<String> paths = new ArrayList<>(List.of("src/Z.java", "README.md"));
        StagedPassportGateResult result = expired(paths);
        paths.clear();

        assertEquals(List.of("README.md", "src/Z.java"), result.relativePaths());
        assertThrows(UnsupportedOperationException.class, () -> result.relativePaths().add("src/A.java"));
        assertThrows(IllegalArgumentException.class,
                () -> expired(List.of("src/Z.java", "src/Z.java")));
    }

    @Test
    void acceptsOnlyAFullFingerprintWhenTheStateHasStagedMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                1, StagedPassportGateState.CURRENT, StagedPassportGateReason.IDENTITY_MATCH,
                "d".repeat(63), 1, 1, 2, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                1, StagedPassportGateState.NO_STAGED_CHANGE, StagedPassportGateReason.NO_INDEX_ENTRIES,
                FINGERPRINT, 0, 0, 0, 0, List.of()));
    }

    @Test
    void permitsAnAttemptOnlyForCurrentAndRequiresItThere() {
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                1, StagedPassportGateState.EXPIRED, StagedPassportGateReason.IDENTITY_CHANGED,
                FINGERPRINT, 1, 1, 2, 1, List.of("src/App.java")));
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                1, StagedPassportGateState.CURRENT, StagedPassportGateReason.IDENTITY_MATCH,
                FINGERPRINT, 0, 1, 2, 1, List.of()));
    }

    @Test
    void rejectsMoreThanThirtyRelativePathsAndPathsOnNonExpiredStates() {
        List<String> paths = java.util.stream.IntStream.range(0, 31)
                .mapToObj(index -> "src/File%02d.java".formatted(index)).toList();
        assertThrows(IllegalArgumentException.class, () -> expired(paths));
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                1, StagedPassportGateState.CURRENT, StagedPassportGateReason.IDENTITY_MATCH,
                FINGERPRINT, 1, 31, 2, 1, List.of("src/App.java")));
    }

    @Test
    void rejectsUnsafeOrInvalidStateData() {
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                2, StagedPassportGateState.UNDEFENDED, StagedPassportGateReason.NO_STAGED_HISTORY,
                FINGERPRINT, 0, 1, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                1, StagedPassportGateState.EXPIRED, StagedPassportGateReason.IDENTITY_CHANGED,
                FINGERPRINT, 0, 1, 0, 0, List.of("../secret.txt")));
        assertThrows(IllegalArgumentException.class, () -> new StagedPassportGateResult(
                1, StagedPassportGateState.UNAVAILABLE, StagedPassportGateReason.GIT_CAPTURE_FAILED,
                "", 0, -1, 0, 0, List.of()));
    }

    @Test
    void pathParsingFailureUsesASourceFreeDiagnostic() {
        String marker = "PRIVATE-PATH-MARKER";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> expired(List.of(marker + "\0.java")));

        assertFalse(exception.getMessage().contains(marker));
        assertFalse(exception.toString().contains(marker));
        assertNull(exception.getCause());
    }

    @Test
    void rejectsDriveRelativeWindowsPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> expired(List.of("C:secret.txt")));
    }

    @Test
    void safeToStringExposesCountsButNotPathsOrFingerprint() {
        StagedPassportGateResult result = expired(List.of("private/Marker.java"));

        assertFalse(result.toString().contains("private/Marker.java"));
        assertFalse(result.toString().contains(FINGERPRINT));
        assertEquals("StagedPassportGateResult[protocolVersion=1, state=EXPIRED, reason=IDENTITY_CHANGED, "
                + "attemptNumber=0, stagedFileCount=1, addedLines=2, deletedLines=1, relativePathCount=1]",
                result.toString());
    }

    private static StagedPassportGateResult expired(List<String> paths) {
        return new StagedPassportGateResult(1, StagedPassportGateState.EXPIRED,
                StagedPassportGateReason.IDENTITY_CHANGED, FINGERPRINT, 0, 1, 2, 1, paths);
    }
}
