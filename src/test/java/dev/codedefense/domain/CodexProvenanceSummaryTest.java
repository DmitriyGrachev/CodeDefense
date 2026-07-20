package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexProvenanceSummaryTest {
    private static final String THREAD_HASH = "a".repeat(64);

    @Test
    void acceptsExactMatchAndCopiesSortedPaths() {
        List<String> paths = new ArrayList<>(List.of("src/B.java", "src/A.java"));

        CodexProvenanceSummary summary = new CodexProvenanceSummary(1,
                CodexProvenanceStatus.EXACT_CHANGE_MATCH, THREAD_HASH, "0.144.0", 2, 2,
                paths, Instant.EPOCH);
        paths.clear();

        assertEquals(List.of("src/A.java", "src/B.java"), summary.matchedRelativePaths());
        assertThrows(UnsupportedOperationException.class,
                () -> summary.matchedRelativePaths().add("src/C.java"));
        assertFalse(summary.toString().contains(THREAD_HASH));
        assertFalse(summary.toString().contains("src/A.java"));
    }

    @Test
    void enforcesStatusCountConsistency() {
        assertThrows(IllegalArgumentException.class, () -> summary(
                CodexProvenanceStatus.EXACT_CHANGE_MATCH, 2, 1, List.of("src/A.java")));
        assertThrows(IllegalArgumentException.class, () -> summary(
                CodexProvenanceStatus.PARTIAL_PATH_MATCH, 2, 2,
                List.of("src/A.java", "src/B.java")));
        assertThrows(IllegalArgumentException.class, () -> summary(
                CodexProvenanceStatus.NO_MATCH, 2, 1, List.of("src/A.java")));
        assertEquals(0, summary(CodexProvenanceStatus.NO_MATCH, 2, 0, List.of()).matchedFileCount());
    }

    @Test
    void unavailableRetainsOnlyCountsAndTimestamp() {
        CodexProvenanceSummary summary = new CodexProvenanceSummary(1,
                CodexProvenanceStatus.UNAVAILABLE, "", "", 2, 0, List.of(), Instant.EPOCH);

        assertEquals("", summary.threadIdentityHash());
        assertEquals("", summary.codexVersion());
        assertThrows(IllegalArgumentException.class, () -> new CodexProvenanceSummary(1,
                CodexProvenanceStatus.UNAVAILABLE, THREAD_HASH, "0.144.0", 2, 0,
                List.of(), Instant.EPOCH));
    }

    @Test
    void rejectsUnsafeIdentityVersionAndPaths() {
        assertThrows(IllegalArgumentException.class, () -> new CodexProvenanceSummary(2,
                CodexProvenanceStatus.NO_MATCH, THREAD_HASH, "0.144.0", 1, 0,
                List.of(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new CodexProvenanceSummary(1,
                CodexProvenanceStatus.NO_MATCH, "A".repeat(64), "0.144.0", 1, 0,
                List.of(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new CodexProvenanceSummary(1,
                CodexProvenanceStatus.NO_MATCH, THREAD_HASH, "latest", 1, 0,
                List.of(), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> summary(
                CodexProvenanceStatus.PARTIAL_PATH_MATCH, 2, 1, List.of("../secret.java")));
        assertThrows(IllegalArgumentException.class, () -> summary(
                CodexProvenanceStatus.PARTIAL_PATH_MATCH, 2, 1, List.of("C:/secret.java")));
        assertThrows(IllegalArgumentException.class, () -> summary(
                CodexProvenanceStatus.PARTIAL_PATH_MATCH, 2, 1,
                List.of("src/A.java", "src/A.java")));
    }

    private static CodexProvenanceSummary summary(CodexProvenanceStatus status,
            int selected, int matched, List<String> paths) {
        return new CodexProvenanceSummary(1, status, THREAD_HASH, "0.144.0",
                selected, matched, paths, Instant.EPOCH);
    }
}
