package dev.codedefense.ai;

import java.time.Duration;
import java.util.Objects;

public record StructuredCodexRequest(
        String operationName,
        String prompt,
        String schemaJson,
        String model,
        ReasoningEffort reasoningEffort,
        Duration timeout) {

    public StructuredCodexRequest {
        requireNonblank(operationName, "Operation name");
        requireNonblank(prompt, "Prompt");
        requireNonblank(schemaJson, "Schema JSON");
        requireNonblank(model, "Model");
        Objects.requireNonNull(reasoningEffort, "Reasoning effort");
        Objects.requireNonNull(timeout, "Timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }

    public static StructuredCodexRequest usingDefaults(
            String operationName, String prompt, String schemaJson, ReasoningEffort reasoningEffort) {
        CodexRuntimeConfig defaults = CodexRuntimeConfig.defaults();
        return new StructuredCodexRequest(operationName, prompt, schemaJson, defaults.defaultModel(),
                reasoningEffort, defaults.defaultExecutionTimeout());
    }

    @Override
    public String toString() {
        return "StructuredCodexRequest[operationName=%s, model=%s, reasoningEffort=%s, timeout=%s]"
                .formatted(operationName, model, reasoningEffort, timeout);
    }

    private static void requireNonblank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be nonblank");
        }
    }
}
