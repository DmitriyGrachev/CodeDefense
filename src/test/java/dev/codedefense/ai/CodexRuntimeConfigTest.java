package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CodexRuntimeConfigTest {
    @Test
    void defaultsMatchIterationFourContract() {
        CodexRuntimeConfig config = CodexRuntimeConfig.defaults();

        assertEquals("gpt-5.6-terra", config.defaultModel());
        assertEquals(Duration.ofSeconds(15), config.environmentCheckTimeout());
        assertEquals(Duration.ofSeconds(180), config.defaultExecutionTimeout());
        assertEquals(Duration.ofSeconds(2), config.terminationGracePeriod());
        assertEquals(16 * 1024, config.maximumCapturedStdoutBytes());
        assertEquals(32 * 1024, config.maximumCapturedStderrBytes());
        assertEquals(1024 * 1024, config.maximumFinalResponseBytes());
    }

    @Test
    void rejectsInvalidRuntimeLimits() {
        CodexRuntimeConfig defaults = CodexRuntimeConfig.defaults();

        assertThrows(IllegalArgumentException.class, () -> new CodexRuntimeConfig(
                " ", defaults.environmentCheckTimeout(), defaults.defaultExecutionTimeout(),
                defaults.terminationGracePeriod(), 1, 1, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new CodexRuntimeConfig(
                defaults.defaultModel(), Duration.ZERO, defaults.defaultExecutionTimeout(),
                defaults.terminationGracePeriod(), 1, 1, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new CodexRuntimeConfig(
                defaults.defaultModel(), defaults.environmentCheckTimeout(), Duration.ofSeconds(-1),
                defaults.terminationGracePeriod(), 1, 1, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new CodexRuntimeConfig(
                defaults.defaultModel(), defaults.environmentCheckTimeout(), defaults.defaultExecutionTimeout(),
                Duration.ZERO, 1, 1, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new CodexRuntimeConfig(
                defaults.defaultModel(), defaults.environmentCheckTimeout(), defaults.defaultExecutionTimeout(),
                defaults.terminationGracePeriod(), 0, 1, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new CodexRuntimeConfig(
                defaults.defaultModel(), defaults.environmentCheckTimeout(), defaults.defaultExecutionTimeout(),
                defaults.terminationGracePeriod(), 1, 0, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new CodexRuntimeConfig(
                defaults.defaultModel(), defaults.environmentCheckTimeout(), defaults.defaultExecutionTimeout(),
                defaults.terminationGracePeriod(), 1, 1, (64 * 1024) - 1));
    }
}
