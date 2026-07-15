package dev.codedefense.ai.exception;

public final class CodexTimeoutException extends CodexException {
    public CodexTimeoutException() {
        super("Codex operation timed out.");
    }

    public CodexTimeoutException(Throwable cause) {
        super("Codex operation timed out.", cause);
    }
}
