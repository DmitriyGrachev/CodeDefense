package dev.codedefense.ai;

import java.util.List;
import java.util.Objects;

public record CodexExecutable(List<String> commandPrefix) {
    public CodexExecutable {
        Objects.requireNonNull(commandPrefix, "Command prefix");
        if (commandPrefix.isEmpty() || commandPrefix.stream().anyMatch(token -> token == null || token.isBlank())) {
            throw new IllegalArgumentException("Codex command prefix must contain nonblank tokens");
        }
        commandPrefix = List.copyOf(commandPrefix);
    }
}
