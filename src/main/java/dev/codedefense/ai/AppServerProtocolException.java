package dev.codedefense.ai;

public final class AppServerProtocolException extends RuntimeException {
    public enum Kind { INVALID_RESPONSE, UNSUPPORTED_METHOD, LIMIT_EXCEEDED, TIMEOUT, EOF, EXECUTION_FAILED }
    private final Kind kind;
    public AppServerProtocolException(Kind kind) {
        super("Codex app-server response was unavailable.");
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
    }
    public AppServerProtocolException(Kind kind, Throwable cause) {
        super("Codex app-server response was unavailable.", cause);
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
    }
    public Kind kind() { return kind; }
}
