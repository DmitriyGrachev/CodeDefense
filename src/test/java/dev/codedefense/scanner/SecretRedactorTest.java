package dev.codedefense.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SecretRedactorTest {
    private final SecretRedactor redactor = new SecretRedactor();

    @Test
    void redactsQuotedJsonKeysAndCountsBearerOnce() {
        var result = redactor.redact("{\"token\": \"secret\", \"apiKey\": \"value\", \"client_secret\": \"other\", \"authorization\": \"Bearer bearer\"}");
        assertFalse(result.content().contains(": \"secret\""));
        assertFalse(result.content().contains(": \"value\""));
        assertFalse(result.content().contains(": \"other\""));
        assertFalse(result.content().contains("bearer\""));
        assertTrue(result.content().contains("\"token\": \"[REDACTED]\""));
        assertEquals(4, result.replacementCount());
    }

    @Test
    void preservesOrdinaryMethodCalls() {
        assertEquals("tokenService.create()", redactor.redact("tokenService.create()").content());
    }
}
