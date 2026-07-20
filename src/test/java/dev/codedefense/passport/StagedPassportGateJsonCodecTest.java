package dev.codedefense.passport;

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

class StagedPassportGateJsonCodecTest {
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void encodesCurrentResultAsExactDeterministicNewlineTerminatedUtf8Json() {
        byte[] encoded = new StagedPassportGateJsonCodec().encode(new StagedPassportGateResult(
                1, StagedPassportGateState.CURRENT, StagedPassportGateReason.IDENTITY_MATCH,
                FINGERPRINT, 2, 3, 12, 4, List.of()));

        assertArrayEquals(("{\"protocolVersion\":1,\"state\":\"CURRENT\",\"reason\":\"IDENTITY_MATCH\","
                + "\"diffFingerprint\":\"" + FINGERPRINT + "\",\"attemptNumber\":2,"
                + "\"stagedFileCount\":3,\"addedLines\":12,\"deletedLines\":4,\"relativePaths\":[]}\n")
                .getBytes(StandardCharsets.UTF_8), encoded);
        assertEquals("\n", new String(encoded, StandardCharsets.UTF_8).substring(encoded.length - 1));
    }

    @Test
    void encodesOnlyExpiredRelativePathsAndKeepsThemSorted() {
        StagedPassportGateResult expired = new StagedPassportGateResult(1,
                StagedPassportGateState.EXPIRED, StagedPassportGateReason.IDENTITY_CHANGED,
                FINGERPRINT, 0, 2, 3, 1, List.of("zeta/File.java", "alpha/File.java"));

        String json = new String(new StagedPassportGateJsonCodec().encode(expired), StandardCharsets.UTF_8);

        assertTrue(json.endsWith("\n"));
        assertTrue(json.contains("\"relativePaths\":[\"alpha/File.java\",\"zeta/File.java\"]"));
        assertFalse(json.contains("source"));
        assertFalse(json.contains("evidence"));
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= 256 * 1024);
    }
}
