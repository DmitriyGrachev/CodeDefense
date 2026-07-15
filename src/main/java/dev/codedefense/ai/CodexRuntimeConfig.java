package dev.codedefense.ai;

import java.time.Duration;
import java.util.Objects;

public record CodexRuntimeConfig(
        String defaultModel,
        Duration environmentCheckTimeout,
        Duration defaultExecutionTimeout,
        Duration terminationGracePeriod,
        int maximumCapturedStdoutBytes,
        int maximumCapturedStderrBytes,
        int maximumFinalResponseBytes) {

    public CodexRuntimeConfig {
        if (defaultModel == null || defaultModel.isBlank()) {
            throw new IllegalArgumentException("Default Codex model must be nonblank");
        }
        requirePositive(environmentCheckTimeout, "Environment check timeout");
        requirePositive(defaultExecutionTimeout, "Execution timeout");
        requirePositive(terminationGracePeriod, "Termination grace period");
        if (maximumCapturedStdoutBytes <= 0 || maximumCapturedStderrBytes <= 0) {
            throw new IllegalArgumentException("Captured output limits must be positive");
        }
        if (maximumFinalResponseBytes < 64 * 1024) {
            throw new IllegalArgumentException("Final response limit must be at least 64 KiB");
        }
    }

    public static CodexRuntimeConfig defaults() {
        return new CodexRuntimeConfig(
                "gpt-5.6-terra",
                Duration.ofSeconds(15),
                Duration.ofSeconds(180),
                Duration.ofSeconds(2),
                16 * 1024,
                32 * 1024,
                1024 * 1024);
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
