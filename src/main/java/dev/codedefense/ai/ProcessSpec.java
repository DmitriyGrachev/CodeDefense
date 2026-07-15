package dev.codedefense.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProcessSpec(
        List<String> command,
        Path workingDirectory,
        Map<String, String> environment,
        String standardInput,
        Duration timeout,
        Duration terminationGracePeriod,
        int maximumStdoutBytes,
        int maximumStderrBytes) {

    public ProcessSpec {
        Objects.requireNonNull(command, "Command");
        if (command.isEmpty() || command.stream().anyMatch(token -> token == null || token.isBlank())) {
            throw new IllegalArgumentException("Command must contain nonblank tokens");
        }
        command = List.copyOf(command);

        Objects.requireNonNull(workingDirectory, "Working directory");
        if (!Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("Working directory must exist and be a directory");
        }

        Objects.requireNonNull(environment, "Environment");
        if (environment.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("Environment keys and values must be non-null");
        }
        environment = Map.copyOf(environment);

        Objects.requireNonNull(standardInput, "Standard input");
        requirePositive(timeout, "Timeout");
        requirePositive(terminationGracePeriod, "Termination grace period");
        if (maximumStdoutBytes <= 0 || maximumStderrBytes <= 0) {
            throw new IllegalArgumentException("Output capture limits must be positive");
        }
    }

    @Override
    public String toString() {
        return "ProcessSpec[command=%s, workingDirectory=%s, environmentKeys=%s, timeout=%s, "
                + "terminationGracePeriod=%s, maximumStdoutBytes=%s, maximumStderrBytes=%s]"
                .formatted(
                        command,
                        workingDirectory,
                        environment.keySet(),
                        timeout,
                        terminationGracePeriod,
                        maximumStdoutBytes,
                        maximumStderrBytes);
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
