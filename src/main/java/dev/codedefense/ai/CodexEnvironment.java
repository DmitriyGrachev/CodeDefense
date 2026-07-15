package dev.codedefense.ai;

import java.util.Objects;

public record CodexEnvironment(CodexExecutable executable, String version) {
    public CodexEnvironment {
        Objects.requireNonNull(executable, "Codex executable");
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Codex version must be nonblank");
        }
    }
}
