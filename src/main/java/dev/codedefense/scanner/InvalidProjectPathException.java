package dev.codedefense.scanner;

public final class InvalidProjectPathException extends RuntimeException {
    public InvalidProjectPathException(String message) {
        super(message);
    }

    public InvalidProjectPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
