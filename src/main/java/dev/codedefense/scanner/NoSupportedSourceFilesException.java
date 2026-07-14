package dev.codedefense.scanner;

public final class NoSupportedSourceFilesException extends RuntimeException {
    public NoSupportedSourceFilesException(String message) {
        super(message);
    }
}
