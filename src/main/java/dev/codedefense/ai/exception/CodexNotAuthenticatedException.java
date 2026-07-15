package dev.codedefense.ai.exception;

public final class CodexNotAuthenticatedException extends CodexException {
    public CodexNotAuthenticatedException() {
        super("Codex CLI is installed but not authenticated.\n\nRun:\n  codex login");
    }

    public CodexNotAuthenticatedException(Throwable cause) {
        super("Codex CLI is installed but not authenticated.\n\nRun:\n  codex login", cause);
    }
}
