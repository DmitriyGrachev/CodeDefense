package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodexBoundaryContractsTest {
    @Test
    void exposesStableReasoningCliValues() {
        assertEquals("minimal", ReasoningEffort.MINIMAL.cliValue());
        assertEquals("low", ReasoningEffort.LOW.cliValue());
        assertEquals("medium", ReasoningEffort.MEDIUM.cliValue());
        assertEquals("high", ReasoningEffort.HIGH.cliValue());
    }

    @Test
    void executableCopiesAndValidatesCommandPrefix() {
        ArrayList<String> command = new ArrayList<>(List.of("codex.cmd"));
        CodexExecutable executable = new CodexExecutable(command);
        command.add("unexpected");

        assertEquals(List.of("codex.cmd"), executable.commandPrefix());
        assertThrows(UnsupportedOperationException.class, () -> executable.commandPrefix().add("mutate"));
        assertThrows(IllegalArgumentException.class, () -> new CodexExecutable(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CodexExecutable(List.of(" ")));
    }

    @Test
    void environmentRequiresExecutableAndVersion() {
        CodexExecutable executable = new CodexExecutable(List.of("codex"));
        CodexEnvironment environment = new CodexEnvironment(executable, "codex-cli 1.2.3");

        assertEquals(executable, environment.executable());
        assertEquals("codex-cli 1.2.3", environment.version());
        assertThrows(NullPointerException.class, () -> new CodexEnvironment(null, "version"));
        assertThrows(IllegalArgumentException.class, () -> new CodexEnvironment(executable, " "));
    }

    @Test
    void providerAndPreflightExposeOnlyStructuredContracts() {
        StructuredCodexResult expected = new StructuredCodexResult("{}", Duration.ZERO, "model");
        AiProvider provider = request -> expected;
        CodexEnvironment environment = new CodexEnvironment(new CodexExecutable(List.of("codex")), "version");
        CodexPreflight preflight = () -> environment;

        assertEquals(expected, provider.execute(StructuredCodexRequest.usingDefaults(
                "operation", "prompt", "{}", ReasoningEffort.LOW)));
        assertEquals(environment, preflight.checkReady());
    }
}
