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
        assertEquals("accessTokenProvider.load()", redactor.redact("accessTokenProvider.load()").content());
    }

    @Test
    void redactsEverySupportedSingleLineFormExactlyOnce() {
        String[] values = {"token=abc", "password: p@ss,word", "apiKey: abc}def", "password=\"secret\"", "password='secret'", "apiKey: \"abc123\"", "Authorization: Bearer abc.def", "{\"token\": \"secret\"}", "{\"token\": \"abc\\\"def\"}", "{\"token\": \"abc\\\\def\"}", "{\"token\": \"secret-without-closing"};
        for (String value : values) {
            var result = redactor.redact(value);
            assertEquals(1, result.replacementCount(), value);
            assertEquals(1, result.content().split("\\[REDACTED]", -1).length - 1, value);
        }
    }

    @Test
    void redactsUnterminatedAssignmentsAndQuotedYamlKeys() {
        String[] values = {"password=\"TOPSECRET-without-closing", "apiKey: \"TOPSECRET-without-closing", "client_secret='TOPSECRET-without-closing", "password=\"abc\\\"TOPSECRET-without-closing", "client_secret='abc\\'TOPSECRET-without-closing", "\"password\": secret", "\"password\": \"TOPSECRET\"", "\"password\": 'TOPSECRET'", "'password': \"TOPSECRET\"", "'password': 'TOPSECRET'", "\"password\": \"TOPSECRET-without-closing", "\"password\": 'TOPSECRET-without-closing", "'password': \"TOPSECRET-without-closing", "'password': 'TOPSECRET-without-closing"};
        for (String value : values) {
            var result = redactor.redact(value);
            assertFalse(result.content().contains("TOPSECRET"));
            assertEquals(1, result.replacementCount(), value);
            assertEquals(1, result.content().split("\\[REDACTED]", -1).length - 1, value);
        }
    }
}
