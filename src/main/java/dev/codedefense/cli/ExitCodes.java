package dev.codedefense.cli;

public final class ExitCodes {
    public static final int SUCCESS = 0;
    public static final int INVALID_USAGE = 2;
    public static final int INVALID_PROJECT_PATH = 3;
    public static final int NO_SUPPORTED_SOURCE_FILES = 4;
    public static final int CODEX_NOT_INSTALLED = 5;
    public static final int CODEX_NOT_AUTHENTICATED = 6;
    public static final int CODEX_EXECUTION_FAILED = 7;
    public static final int INVALID_MODEL_RESPONSE = 8;
    public static final int REPORT_PERSISTENCE_FAILED = 9;
    public static final int CANCELLED = 130;

    private ExitCodes() {
    }
}
