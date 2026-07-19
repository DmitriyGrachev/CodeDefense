package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.PassportStatus;
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

    private void assertSafeFailure(byte[] input) {
        ChangePassportPersistenceException exception = assertThrows(
                ChangePassportPersistenceException.class, () -> codec.decode(input));
        assertEquals("Unable to read a Change Passport receipt.", exception.getMessage());
        assertFalse(exception.getMessage().contains("private-answer"));
    }
}
