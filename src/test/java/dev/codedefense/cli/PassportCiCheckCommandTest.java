package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ci.CiPassportStatus;
import dev.codedefense.ci.CommitContinuityResult;
import dev.codedefense.ci.PassportContinuityRenderer;
import dev.codedefense.ci.PassportContinuityResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class PassportCiCheckCommandTest {
    @Test
    void advisoryAndRequiredPoliciesRenderSourceFreeGitHubOutput() {
        var result = new PassportContinuityResult(List.of(
                new CommitContinuityResult("a".repeat(40), CiPassportStatus.MISMATCH,
                        "b".repeat(64), "c".repeat(64))));
        var command = new PassportCiCheckCommand((path, base, head) -> result,
                new PassportContinuityRenderer());
        StringWriter output = new StringWriter();
        CommandLine cli = new CommandLine(command).setOut(new PrintWriter(output, true));

        assertEquals(0, cli.execute("--base", "main", "--head", "HEAD", "--format", "github"));
        String rendered = output.toString();
        assertTrue(rendered.contains("CodeDefense Passport continuity"));
        assertTrue(rendered.contains("aaaaaaaaaaaa"));
        assertTrue(rendered.contains("Codex was not invoked."));
        assertFalse(rendered.contains("source code"));
        assertFalse(rendered.contains("commit message"));
        assertFalse(rendered.contains("::"));

        assertEquals(1, new CommandLine(command).execute(
                "--base", "main", "--head", "HEAD", "--policy", "required"));
    }
}
