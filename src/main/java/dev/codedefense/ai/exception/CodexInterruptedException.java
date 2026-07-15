package dev.codedefense.ai.exception;

public final class CodexInterruptedException extends CodexException {
    public CodexInterruptedException(Throwable cause) {
        super("Codex operation was interrupted.", cause);
    }
}
