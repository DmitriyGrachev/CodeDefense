package dev.codedefense.ai;

import java.time.Duration;
import java.util.Objects;

public record StructuredCodexResult(String finalJson, Duration duration, String model) {
    public StructuredCodexResult {
        if (finalJson == null || finalJson.isBlank()) {
            throw new IllegalArgumentException("Final JSON must be nonblank");
        }
        Objects.requireNonNull(duration, "Duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model must be nonblank");
        }
    }

    @Override
    public String toString() {
        return "StructuredCodexResult[duration=%s, model=%s]".formatted(duration, model);
    }
}
