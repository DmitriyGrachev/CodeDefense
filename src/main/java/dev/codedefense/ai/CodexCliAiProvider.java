package dev.codedefense.ai;

import java.util.Objects;
import java.util.function.BiFunction;

/** Lazily initializes Codex once and delegates structured requests to its process runner. */
public final class CodexCliAiProvider implements AiProvider {
    private final CodexPreflight preflight;
    private final BiFunction<CodexEnvironment, StructuredCodexRequest, StructuredCodexResult> runner;
    private volatile CodexEnvironment environment;

    public CodexCliAiProvider(CodexPreflight preflight, CodexProcessRunner runner) {
        this(preflight, Objects.requireNonNull(runner, "Codex process runner")::execute);
    }

    CodexCliAiProvider(
            CodexPreflight preflight,
            BiFunction<CodexEnvironment, StructuredCodexRequest, StructuredCodexResult> runner) {
        this.preflight = Objects.requireNonNull(preflight, "Codex preflight");
        this.runner = Objects.requireNonNull(runner, "Codex process runner");
    }

    @Override
    public StructuredCodexResult execute(StructuredCodexRequest request) {
        Objects.requireNonNull(request, "Structured Codex request");
        return runner.apply(readyEnvironment(), request);
    }

    private CodexEnvironment readyEnvironment() {
        CodexEnvironment ready = environment;
        if (ready != null) {
            return ready;
        }
        synchronized (this) {
            if (environment == null) {
                environment = Objects.requireNonNull(preflight.checkReady(), "Codex preflight result");
            }
            return environment;
        }
    }
}
