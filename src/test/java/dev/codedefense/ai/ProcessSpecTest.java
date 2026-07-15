package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessSpecTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesInputCollectionsAndKeepsSensitiveValuesOutOfToString() {
        List<String> command = new ArrayList<>(List.of("java", "Fixture Main"));
        Map<String, String> environment = new HashMap<>(Map.of("API_TOKEN", "environment-secret"));

        ProcessSpec spec = new ProcessSpec(
                command,
                temporaryDirectory,
                environment,
                "standard-input-secret",
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                64,
                128);
        command.add("later-token");
        environment.put("ADDED_LATER", "another-secret");

        assertEquals(List.of("java", "Fixture Main"), spec.command());
        assertEquals(Map.of("API_TOKEN", "environment-secret"), spec.environment());
        assertThrows(UnsupportedOperationException.class, () -> spec.command().add("mutate"));
        assertThrows(UnsupportedOperationException.class, () -> spec.environment().put("MUTATE", "value"));
        assertFalse(spec.toString().contains("standard-input-secret"));
        assertFalse(spec.toString().contains("environment-secret"));
    }

    @Test
    void rejectsInvalidProcessConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> spec(List.of(), Map.of(), Duration.ofSeconds(1), 1));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of(" "), Map.of(), Duration.ofSeconds(1), 1));
        List<String> commandWithNullToken = new ArrayList<>(List.of("java"));
        commandWithNullToken.add(null);
        assertThrows(IllegalArgumentException.class, () -> spec(
                commandWithNullToken, Map.of(), Duration.ofSeconds(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new ProcessSpec(
                List.of("java"), temporaryDirectory.resolve("missing"), Map.of(), "",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1));
        assertThrows(NullPointerException.class, () -> new ProcessSpec(
                List.of("java"), temporaryDirectory, Map.of(), null,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of("java"), Map.of(), Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of("java"), Map.of(), Duration.ofSeconds(1), 0));
    }

    private ProcessSpec spec(List<String> command, Map<String, String> environment, Duration timeout, int limit) {
        return new ProcessSpec(
                command,
                temporaryDirectory,
                environment,
                "",
                timeout,
                Duration.ofMillis(100),
                limit,
                limit);
    }
}
