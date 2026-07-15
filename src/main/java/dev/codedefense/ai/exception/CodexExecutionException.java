package dev.codedefense.ai.exception;

public final class CodexExecutionException extends CodexException {
    private final int exitCode;

    public CodexExecutionException(int exitCode, String diagnostic) {
        super(message(exitCode, diagnostic));
        this.exitCode = exitCode;
    }

    public CodexExecutionException(int exitCode, String diagnostic, Throwable cause) {
        super(message(exitCode, diagnostic), cause);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }

    private static String message(int exitCode, String diagnostic) {
        String prefix = "Codex execution failed with exit code " + exitCode + ".";
        if (diagnostic == null || diagnostic.isBlank()) {
            return prefix;
        }
        return prefix + "\n" + diagnostic.replace("\r\n", "\n").replace('\r', '\n');
    }
}
