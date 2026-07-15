package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexCommandFactoryTest {
    @Test
    void createsTheExactSafeTokenSequenceWithoutPromptOrSchema() {
        List<String> prefix = new ArrayList<>(List.of("codex.cmd"));
        StructuredCodexRequest request = new StructuredCodexRequest(
                "operation", "private prompt", "{\"private\":\"schema\"}", "model with spaces",
                ReasoningEffort.MEDIUM, Duration.ofSeconds(30));
        Path workspace = Path.of("temporary workspace");
        Path schema = workspace.resolve("schema file.json");
        Path output = workspace.resolve("final output.json");

        List<String> command = new CodexCommandFactory().create(
                new CodexExecutable(prefix), request, workspace, schema, output);
        prefix.add("mutate");

        assertEquals(List.of(
                "codex.cmd", "--ask-for-approval", "never", "exec", "--ephemeral", "--ignore-user-config", "--sandbox", "read-only",
                "--skip-git-repo-check", "--color", "never",
                "--model", "model with spaces", "--config", "model_reasoning_effort=\"medium\"",
                "--cd", workspace.toString(), "--output-schema", schema.toString(),
                "--output-last-message", output.toString(), "-"), command);
        assertFalse(command.contains("private prompt"));
        assertFalse(command.contains("{\"private\":\"schema\"}"));
        assertFalse(command.contains("--yolo"));
        assertFalse(command.contains("danger-full-access"));
        assertFalse(command.contains("workspace-write"));
        assertFalse(command.contains("--ignore-rules"));
        assertEquals("--ask-for-approval", command.get(1));
        assertEquals("-", command.getLast());
        assertThrows(UnsupportedOperationException.class, () -> command.add("mutate"));
    }
}
