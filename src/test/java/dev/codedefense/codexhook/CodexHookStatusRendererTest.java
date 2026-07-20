package dev.codedefense.codexhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.domain.StagedPassportGateReason;
import dev.codedefense.domain.StagedPassportGateResult;
import dev.codedefense.domain.StagedPassportGateState;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexHookStatusRendererTest {
    private static final String FINGERPRINT = "a".repeat(64);
    private final CodexHookStatusRenderer renderer = new CodexHookStatusRenderer();

    @Test
    void noStagedChangeAndInvalidRepositoryAreSilent() {
        assertArrayEquals(new byte[0], render(noStaged()));
        assertArrayEquals(new byte[0], render(unavailable(StagedPassportGateReason.INVALID_REPOSITORY)));
    }

    @Test
    void rendersExactUndefendedGuidance() {
        assertEquals(("{\"continue\":true,\"systemMessage\":\"CodeDefense: UNDEFENDED — "
                + "2 staged files, +18/-4.\\nRun a CodeDefense staged defense before committing.\"}\n"),
                text(undefended()));
    }

    @Test
    void rendersOnlyTheShortCurrentFingerprintAndAttempt() {
        String output = text(current());

        assertEquals("{\"continue\":true,\"systemMessage\":\"CodeDefense: CURRENT — "
                + "Passport aaaaaaaaaaaa, attempt 3.\"}\n", output);
        assertFalse(output.contains(FINGERPRINT));
    }

    @Test
    void expiredGuidanceOmitsRelativePathsAndRawGateFields() {
        String output = text(expired());

        assertEquals(("{\"continue\":true,\"systemMessage\":\"CodeDefense: EXPIRED — "
                + "2 staged files, +18/-4; the staged change no longer matches its Passport.\\n"
                + "Run a new defense for the current staged change.\"}\n"), output);
        assertFalse(output.contains("private/marker.java"));
        assertFalse(output.contains("relativePaths"));
        assertFalse(output.contains("diffFingerprint"));
    }

    @Test
    void operationalFailuresUseOneSafeAvailabilityWarning() {
        String gitFailure = text(unavailable(StagedPassportGateReason.GIT_CAPTURE_FAILED));
        String storeFailure = text(unavailable(StagedPassportGateReason.PASSPORT_STORE_FAILED));

        assertEquals("{\"continue\":true,\"systemMessage\":\"CodeDefense: UNAVAILABLE — "
                + "staged Passport status could not be determined safely.\"}\n", gitFailure);
        assertEquals(gitFailure, storeFailure);
    }

    @Test
    void everyMessageIsOneBoundedNewlineTerminatedUtf8JsonObject() throws Exception {
        for (StagedPassportGateResult result : List.of(undefended(), current(), expired(),
                unavailable(StagedPassportGateReason.GIT_CAPTURE_FAILED))) {
            byte[] encoded = renderer.render(result).orElseThrow();
            String output = new String(encoded, StandardCharsets.UTF_8);
            JsonNode json = new ObjectMapper().readTree(encoded);

            assertTrue(json.path("continue").asBoolean());
            assertEquals(2, json.size());
            assertTrue(output.endsWith("\n"));
            assertFalse(output.endsWith("\n\n"));
            assertTrue(encoded.length <= CodexHookStatusRenderer.MAXIMUM_OUTPUT_BYTES);
            for (String forbidden : List.of("source", "question", "answer", "feedback", "evidence")) {
                assertFalse(output.toLowerCase().contains(forbidden));
            }
        }
    }

    private byte[] render(StagedPassportGateResult result) {
        return renderer.render(result).orElseGet(() -> new byte[0]);
    }

    private String text(StagedPassportGateResult result) {
        return new String(renderer.render(result).orElseThrow(), StandardCharsets.UTF_8);
    }

    private static StagedPassportGateResult noStaged() {
        return new StagedPassportGateResult(1, StagedPassportGateState.NO_STAGED_CHANGE,
                StagedPassportGateReason.NO_INDEX_ENTRIES, "", 0, 0, 0, 0, List.of());
    }

    private static StagedPassportGateResult undefended() {
        return new StagedPassportGateResult(1, StagedPassportGateState.UNDEFENDED,
                StagedPassportGateReason.NO_STAGED_HISTORY, FINGERPRINT, 0, 2, 18, 4, List.of());
    }

    private static StagedPassportGateResult current() {
        return new StagedPassportGateResult(1, StagedPassportGateState.CURRENT,
                StagedPassportGateReason.IDENTITY_MATCH, FINGERPRINT, 3, 2, 18, 4, List.of());
    }

    private static StagedPassportGateResult expired() {
        return new StagedPassportGateResult(1, StagedPassportGateState.EXPIRED,
                StagedPassportGateReason.IDENTITY_CHANGED, FINGERPRINT, 0, 2, 18, 4,
                List.of("private/marker.java"));
    }

    private static StagedPassportGateResult unavailable(StagedPassportGateReason reason) {
        return new StagedPassportGateResult(1, StagedPassportGateState.UNAVAILABLE,
                reason, "", 0, 0, 0, 0, List.of());
    }
}
