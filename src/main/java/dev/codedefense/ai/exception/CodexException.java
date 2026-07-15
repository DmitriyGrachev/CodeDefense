package dev.codedefense.ai.exception;

public abstract class CodexException extends RuntimeException {
    protected CodexException(String message) {
        super(message);
    }

    protected CodexException(String message, Throwable cause) {
        super(message, cause);
    }
}
