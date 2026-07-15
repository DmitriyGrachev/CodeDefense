package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodexProcessEnvironmentTest {
    @Test
    void removesSensitiveKeysAndPreservesExplicitlySafeAndOrdinaryKeys() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("OPENAI_API_KEY", "openai-secret");
        source.put("GITHUB_TOKEN", "github-secret");
        source.put("custom_password", "password-secret");
        source.put("MY_CONNECTION_STRING", "connection-secret");
        source.put("PATH", "platform-path");
        source.put("CODEX_HOME", "codex-home");
        source.put("HTTP_PROXY", "http://proxy.example");
        source.put("ORDINARY_VARIABLE", "ordinary-value");

        Map<String, String> sanitized = new CodexProcessEnvironment().sanitize(source);

        assertFalse(sanitized.containsKey("OPENAI_API_KEY"));
        assertFalse(sanitized.containsKey("GITHUB_TOKEN"));
        assertFalse(sanitized.containsKey("custom_password"));
        assertFalse(sanitized.containsKey("MY_CONNECTION_STRING"));
        assertEquals("platform-path", sanitized.get("PATH"));
        assertEquals("codex-home", sanitized.get("CODEX_HOME"));
        assertEquals("http://proxy.example", sanitized.get("HTTP_PROXY"));
        assertEquals("ordinary-value", sanitized.get("ORDINARY_VARIABLE"));
        assertEquals("openai-secret", source.get("OPENAI_API_KEY"));
        assertEquals("github-secret", source.get("GITHUB_TOKEN"));
        assertThrows(UnsupportedOperationException.class, () -> sanitized.put("NEW", "value"));
    }

    @Test
    void rejectsNullKeysAndValuesAndNeverExposesValuesInToString() {
        Map<String, String> nullKey = new LinkedHashMap<>();
        nullKey.put(null, "value");
        Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("KEY", null);

        CodexProcessEnvironment environment = new CodexProcessEnvironment();

        assertThrows(NullPointerException.class, () -> environment.sanitize(nullKey));
        assertThrows(NullPointerException.class, () -> environment.sanitize(nullValue));
        assertFalse(environment.toString().contains("ordinary-value"));
        assertTrue(environment.toString().contains("CodexProcessEnvironment"));
    }
}
