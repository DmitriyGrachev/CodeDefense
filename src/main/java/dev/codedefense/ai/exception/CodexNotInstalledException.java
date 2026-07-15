package dev.codedefense.ai.exception;

public final class CodexNotInstalledException extends CodexException {
    public CodexNotInstalledException() {
        super("Codex CLI was not found.\n\nInstall Codex, then run:\n  codex login");
    }

    public CodexNotInstalledException(Throwable cause) {
        super("Codex CLI was not found.\n\nInstall Codex, then run:\n  codex login", cause);
    }
}
