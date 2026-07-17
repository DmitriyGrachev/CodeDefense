package dev.codedefense.report;

public final class ReportPersistenceException extends RuntimeException {
    public static final String SAVE_FAILURE_MESSAGE = "CodeDefense could not save the report.";
    public static final String READ_FAILURE_MESSAGE = "CodeDefense could not read the latest report.";

    private ReportPersistenceException(String message) {
        super(message);
    }

    public static ReportPersistenceException saveFailure() {
        return new ReportPersistenceException(SAVE_FAILURE_MESSAGE);
    }

    public static ReportPersistenceException readFailure() {
        return new ReportPersistenceException(READ_FAILURE_MESSAGE);
    }
}
