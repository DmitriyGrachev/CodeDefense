package dev.codedefense.ai;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record CodexExecutable(List<String> commandPrefix) {
    public CodexExecutable {
        Objects.requireNonNull(commandPrefix, "Command prefix");
        if (commandPrefix.isEmpty() || commandPrefix.stream().anyMatch(token -> token == null || token.isBlank())) {
            throw new IllegalArgumentException("Codex command prefix must contain nonblank tokens");
        }
        commandPrefix = List.copyOf(commandPrefix);
    }

    /**
     * Windows PowerShell treats a bare {@code -} after {@code -File script.ps1} as a script parameter instead of
     * forwarding it to the npm shim. Codex reads stdin when its prompt argument is absent, so that launcher must use
     * the implicit form.
     */
    public boolean requiresImplicitStdinPrompt() {
        return commandPrefix.size() >= 2
                && commandPrefix.get(commandPrefix.size() - 2).equalsIgnoreCase("-File")
                && commandPrefix.getLast().toLowerCase(Locale.ROOT).endsWith(".ps1");
    }
}
