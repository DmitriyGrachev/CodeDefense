package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.CodexProvenanceStatus;
import dev.codedefense.domain.CodexProvenanceSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PassportReceiptJsonCodecTest {
    private final PassportReceiptJsonCodec codec = new PassportReceiptJsonCodec();
    private final PassportReceipt receipt = PassportReceipt.from(
            PassportTestFixtures.passport(PassportStatus.CURRENT),
            "7bd53719-1de8-4c78-a48a-430aa38555dc");

    @Test
    void roundTripsDeterministicSourceFreeJson() {
        byte[] first = codec.encode(receipt);
        byte[] second = codec.encode(receipt);
        String json = new String(first, StandardCharsets.UTF_8);

        assertArrayEquals(first, second);
        assertTrue(json.endsWith("\n"));
        assertEquals(receipt, codec.decode(first));
        for (String forbidden : new String[] {"private-answer", "feedback", "expected-key-point",
                "evidence-reason", "prompt"}) {
            assertFalse(json.contains(forbidden), forbidden);
        }
    }

    @Test
    void rejectsUnknownFieldsTrailingTokensAndInvalidUtf8WithOneSafeError() {
        String valid = new String(codec.encode(receipt), StandardCharsets.UTF_8).stripTrailing();
        assertSafeFailure((valid.substring(0, valid.length() - 1) + ",\"unknown\":true}\n")
                .getBytes(StandardCharsets.UTF_8));
        assertSafeFailure((valid + " {}\n").getBytes(StandardCharsets.UTF_8));
        assertSafeFailure(new byte[] {(byte) 0xc3, (byte) 0x28});
    }

    @Test
    void rejectsMissingFieldsFractionalIntegersAndOversizedInput() {
        String valid = new String(codec.encode(receipt), StandardCharsets.UTF_8);
        assertSafeFailure(valid.replaceFirst("\"schemaVersion\":\\d+,", "")
                .getBytes(StandardCharsets.UTF_8));
        assertSafeFailure(valid.replaceFirst("\"overallScore\":55", "\"overallScore\":55.5")
                .getBytes(StandardCharsets.UTF_8));
        assertSafeFailure(new byte[256 * 1024 + 1]);
    }

    @Test
    void roundTripsSchemaFourWithoutThreadOrTranscriptContent() {
        ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        CodexProvenanceSummary summary = new CodexProvenanceSummary(1,
                CodexProvenanceStatus.PARTIAL_PATH_MATCH, "e".repeat(64), "0.144.0",
                2, 1, List.of("src/App.java"), Instant.parse("2026-07-19T12:00:00Z"));
        ChangePassport passport = new ChangePassport(original.change(), original.changeKind(),
                original.sourceIdentity(), original.analysis(), original.session(), original.createdAt(),
                original.model(), original.statusAtCreation(), original.focus(), Optional.of(summary));
        PassportReceipt schemaFour = PassportReceipt.from(passport,
                "7bd53719-1de8-4c78-a48a-430aa38555dc");

        String json = new String(codec.encode(schemaFour), StandardCharsets.UTF_8);

        assertEquals(4, schemaFour.schemaVersion());
        assertEquals(schemaFour, codec.decode(codec.encode(schemaFour)));
        assertTrue(json.contains("\"codexProvenance\""));
        assertFalse(json.contains("private-thread-id"));
        assertFalse(json.contains("PRIVATE-TRANSCRIPT"));
        assertFalse(json.contains("@@ -1 +1 @@"));
    }

    private void assertSafeFailure(byte[] input) {
        ChangePassportPersistenceException exception = assertThrows(
                ChangePassportPersistenceException.class, () -> codec.decode(input));
        assertEquals("Unable to read a Change Passport receipt.", exception.getMessage());
        assertFalse(exception.getMessage().contains("private-answer"));
    }
}
