package dev.codedefense.ai;

/** Indicates that an operating-system process could not be started. */
public final class ProcessStartException extends RuntimeException {
    public ProcessStartException(Throwable cause) {
        super("Unable to start process.", cause);
    }
}
