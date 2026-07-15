package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.CodexEnvironment;
import dev.codedefense.ai.CodexExecutable;
import dev.codedefense.ai.CodexPreflight;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.CodeDefenseApplication;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.terminal.ConfirmationPrompt;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StartCommandCodexPreflightTest {
    @TempDir
    Path root;

    @Test
    void yesBypassesConfirmationCallsPreflightAndReportsReadiness() throws Exception {
        CountingPreflight preflight = new CountingPreflight(readyEnvironment());
        ConfirmationPrompt confirmation = prompt -> {
            throw new AssertionError("Confirmation must be bypassed by --yes");
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(preflight, confirmation, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--yes", root.toString()));
        assertEquals(1, preflight.calls);
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Codex CLI is installed and authenticated."));
        assertTrue(text.contains("Version: codex 1.2.3"));
        assertTrue(text.contains("Project analysis will be connected in Iteration 5."));
    }

    @Test
    void dryRunNeverCallsPreflight() throws Exception {
        CountingPreflight preflight = new CountingPreflight(readyEnvironment());
        ConfirmationPrompt confirmation = prompt -> {
            throw new AssertionError("Dry run must not ask for confirmation");
        };
        CommandLine commandLine = commandLine(
                preflight, confirmation, new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--dry-run", root.toString()));
        assertEquals(0, preflight.calls);
    }

    @Test
    void declinedConfirmationNeverCallsPreflight() throws Exception {
        CountingPreflight preflight = new CountingPreflight(readyEnvironment());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(preflight, prompt -> false, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute(root.toString()));
        assertEquals(0, preflight.calls);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Cancelled before any source content was sent."));
    }

    @Test
    void mapsMissingCodexToTheDocumentedExitCode() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(
                () -> {
                    throw new CodexNotInstalledException();
                },
                prompt -> true,
                new ByteArrayOutputStream(),
                error);

        assertEquals(ExitCodes.CODEX_NOT_INSTALLED, commandLine.execute(root.toString()));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("Codex CLI was not found."));
    }

    @Test
    void mapsMissingAuthenticationToTheDocumentedExitCode() throws Exception {
        CommandLine commandLine = commandLine(
                () -> {
                    throw new CodexNotAuthenticatedException();
                },
                prompt -> true,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream());

        assertEquals(ExitCodes.CODEX_NOT_AUTHENTICATED, commandLine.execute(root.toString()));
    }

    @Test
    void mapsTimeoutAndExecutionFailuresToTheDocumentedExitCode() throws Exception {
        CommandLine timeout = commandLine(
                () -> {
                    throw new CodexTimeoutException();
                },
                prompt -> true,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream());
        CommandLine execution = commandLine(
                () -> {
                    throw new CodexExecutionException(9, "safe diagnostic");
                },
                prompt -> true,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream());

        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, timeout.execute(root.toString()));
        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, execution.execute(root.toString()));
    }

    @Test
    void mapsInterruptedPreflightToCancellationAndPreservesTheInterruptFlag() throws Exception {
        CommandLine commandLine = commandLine(
                () -> {
                    throw new CodexInterruptedException(new InterruptedException("interrupted"));
                },
                prompt -> true,
                new ByteArrayOutputStream(),
                new ByteArrayOutputStream());

        try {
            assertEquals(ExitCodes.CANCELLED, commandLine.execute(root.toString()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void helpAndVersionNeverCallPreflight() throws Exception {
        CountingPreflight preflight = new CountingPreflight(readyEnvironment());
        StartCommand start = new StartCommand(
                validScanner(), new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), prompt -> true, preflight);
        CommandLine commandLine = CodeDefenseApplication.createCommandLine(start);

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--version"));
        assertEquals(ExitCodes.SUCCESS, commandLine.execute("start", "--help"));
        assertEquals(0, preflight.calls);
    }

    private CommandLine commandLine(
            CodexPreflight preflight,
            ConfirmationPrompt confirmation,
            ByteArrayOutputStream output,
            ByteArrayOutputStream error) throws Exception {
        CommandLine commandLine = new CommandLine(new StartCommand(
                validScanner(), new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), confirmation, preflight));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));
        return commandLine;
    }

    private ProjectScanner validScanner() throws Exception {
        Path file = root.resolve("App.java");
        Files.writeString(file, "class App {}");
        long size = Files.size(file);
        return (path, policy) -> new ScanSummary(root, 1, 0, List.of(new SourceFile(file.getFileName(), size)));
    }

    private static CodexEnvironment readyEnvironment() {
        return new CodexEnvironment(new CodexExecutable(List.of("codex")), "codex 1.2.3");
    }

    private static final class CountingPreflight implements CodexPreflight {
        private final CodexEnvironment environment;
        private int calls;

        private CountingPreflight(CodexEnvironment environment) {
            this.environment = environment;
        }

        @Override
        public CodexEnvironment checkReady() {
            calls++;
            return environment;
        }
    }
}
