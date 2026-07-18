package dev.codedefense.sample;

/** Safe, user-facing failures while preparing or removing the embedded sample project. */
public final class SampleProjectException extends RuntimeException {
    public static final String UNAVAILABLE_MESSAGE = "Embedded sample project is unavailable.";
    public static final String PREPARATION_FAILURE_MESSAGE = "Embedded sample project could not be prepared.";
    public static final String CLEANUP_FAILURE_MESSAGE = "Temporary sample project could not be removed.";

    private SampleProjectException(String message, Throwable cause) {
        super(message, cause);
    }

    public static SampleProjectException unavailable() {
        return new SampleProjectException(UNAVAILABLE_MESSAGE, null);
    }

    public static SampleProjectException preparationFailure(Throwable cause) {
        return new SampleProjectException(PREPARATION_FAILURE_MESSAGE, cause);
    }

    public static SampleProjectException cleanupFailure(Throwable cause) {
        return new SampleProjectException(CLEANUP_FAILURE_MESSAGE, cause);
    }
}
