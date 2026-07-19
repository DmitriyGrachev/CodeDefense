package dev.codedefense.bridge;

/** Safe protocol failure that never includes raw bridge content. */
public final class BridgeProtocolException extends RuntimeException {
    public BridgeProtocolException(String message) {
        super(message);
    }

    public BridgeProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
