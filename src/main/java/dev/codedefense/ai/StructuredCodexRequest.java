package dev.codedefense.ai;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

public record StructuredCodexRequest(
        String operationName,
        String prompt,
        String schemaJson,
        String model,
        ReasoningEffort reasoningEffort,
        Duration timeout) {
    private static final Pattern SAFE_OPERATION_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public StructuredCodexRequest {
        requireSafeOperationName(operationName);
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

    private static void requireSafeOperationName(String value) {
        requireNonblank(value, "Operation name");
        if (!SAFE_OPERATION_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Operation name must be a safe identifier");
        }
    }
}
