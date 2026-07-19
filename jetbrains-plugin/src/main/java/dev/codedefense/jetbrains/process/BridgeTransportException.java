package dev.codedefense.jetbrains.process;

public final class BridgeTransportException extends RuntimeException {
    public BridgeTransportException(String message) {
        super(message);
    }

    public BridgeTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
