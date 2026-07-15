package dev.codedefense.ai.exception;

public final class InvalidCodexResponseException extends CodexException {
    public InvalidCodexResponseException(String message) {
        super(message);
    }

    public InvalidCodexResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
