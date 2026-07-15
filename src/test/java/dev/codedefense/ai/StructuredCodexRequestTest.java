package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StructuredCodexRequestTest {
    @Test
    void retainsExplicitValuesWithoutLeakingPromptOrSchema() {
        StructuredCodexRequest request = new StructuredCodexRequest(
                "project-analysis",
                "private repository prompt",
                "{\"privateSchema\":true}",
                "gpt-5.6-terra",
                ReasoningEffort.MEDIUM,
                Duration.ofSeconds(45));

        assertEquals("project-analysis", request.operationName());
        assertEquals("private repository prompt", request.prompt());
        assertEquals("{\"privateSchema\":true}", request.schemaJson());
        assertEquals("gpt-5.6-terra", request.model());
        assertEquals(ReasoningEffort.MEDIUM, request.reasoningEffort());
        assertEquals(Duration.ofSeconds(45), request.timeout());
        assertTrue(request.toString().contains("project-analysis"));
        assertFalse(request.toString().contains("private repository prompt"));
        assertFalse(request.toString().contains("privateSchema"));
    }

    @Test
    void factoryUsesRuntimeDefaults() {
        StructuredCodexRequest request = StructuredCodexRequest.usingDefaults(
                "smoke", "prompt", "{}", ReasoningEffort.LOW);

        CodexRuntimeConfig defaults = CodexRuntimeConfig.defaults();
        assertEquals(defaults.defaultModel(), request.model());
        assertEquals(defaults.defaultExecutionTimeout(), request.timeout());
    }

    @Test
    void rejectsInvalidRequestFields() {
        assertThrows(IllegalArgumentException.class, () -> request(" ", "prompt", "{}", "model", ReasoningEffort.LOW, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> request("operation", " ", "{}", "model", ReasoningEffort.LOW, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> request("operation", "prompt", " ", "model", ReasoningEffort.LOW, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> request("operation", "prompt", "{}", " ", ReasoningEffort.LOW, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> request("operation", "prompt", "{}", "model", null, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> request("operation", "prompt", "{}", "model", ReasoningEffort.LOW, Duration.ZERO));
    }

    private StructuredCodexRequest request(String operationName, String prompt, String schemaJson, String model,
            ReasoningEffort reasoningEffort, Duration timeout) {
        return new StructuredCodexRequest(operationName, prompt, schemaJson, model, reasoningEffort, timeout);
    }
}
