package dev.codedefense.ai;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds non-interactive Codex command tokens without involving a shell. */
public final class CodexCommandFactory {
    public List<String> create(
            CodexExecutable executable,
            StructuredCodexRequest request,
            Path workspace,
            Path schemaPath,
            Path finalMessagePath) {
        Objects.requireNonNull(executable, "Codex executable");
        Objects.requireNonNull(request, "Structured Codex request");
        Objects.requireNonNull(workspace, "Workspace");
        Objects.requireNonNull(schemaPath, "Schema path");
        Objects.requireNonNull(finalMessagePath, "Final message path");

        List<String> command = new ArrayList<>(executable.commandPrefix());
        command.addAll(List.of(
                "exec",
                "--ephemeral",
                "--ignore-user-config",
                "--sandbox",
                "read-only",
                "--ask-for-approval",
                "never",
                "--skip-git-repo-check",
                "--color",
                "never",
                "--model",
                request.model(),
                "--config",
                "model_reasoning_effort=\"" + request.reasoningEffort().cliValue() + "\"",
                "--cd",
                workspace.toString(),
                "--output-schema",
                schemaPath.toString(),
                "--output-last-message",
                finalMessagePath.toString(),
                "-"));
        return List.copyOf(command);
    }
}
