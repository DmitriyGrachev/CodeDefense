package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.analysis.ProjectAnalyzer;
import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.application.CodeDefenseRuntime;
import dev.codedefense.application.DefaultProjectDefenseRunner;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.terminal.ConfirmationPrompt;
import dev.codedefense.terminal.ProjectAnalysisRenderer;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StartCommandProjectAnalysisTest {
    @TempDir
    Path root;

    @Test
    void dryRunSkipsTheAnalyzer() throws Exception {
        CountingAnalyzer analyzer = new CountingAnalyzer(analysis());
        CountingConfirmation confirmation = new CountingConfirmation(true);
        CommandLine commandLine = commandLine(analyzer, confirmation, new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--dry-run", root.toString()));
        assertEquals(0, analyzer.calls);
        assertEquals(0, confirmation.calls);
    }

    @Test
    void declinedConfirmationSkipsTheAnalyzer() throws Exception {
        CountingAnalyzer analyzer = new CountingAnalyzer(analysis());
        CountingConfirmation confirmation = new CountingConfirmation(false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(analyzer, confirmation, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute(root.toString()));
        assertEquals(0, analyzer.calls);
        assertEquals(1, confirmation.calls);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Cancelled before any source content was sent."));
    }

    @Test
    void acceptedConfirmationAnalyzesOnceAndRendersTheOverview() throws Exception {
        CountingAnalyzer analyzer = new CountingAnalyzer(analysis());
        CountingConfirmation confirmation = new CountingConfirmation(true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(analyzer, confirmation, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute(root.toString()));
        assertEquals(1, confirmation.calls);
        assertEquals(1, analyzer.calls);
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("Analyzing project with GPT-5.6..."));
        assertTrue(rendered.contains("Prepared technical questions: 3"));
        assertTrue(rendered.contains("Project analysis completed."));
    }

    @Test
    void yesAnalyzesOnceAndRendersTheOverview() throws Exception {
        CountingAnalyzer analyzer = new CountingAnalyzer(analysis());
        CountingConfirmation confirmation = new CountingConfirmation(false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(analyzer, confirmation, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--yes", root.toString()));
        assertEquals(1, analyzer.calls);
        assertEquals(0, confirmation.calls);
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertTrue(rendered.contains("Project: Fixture project"));
        assertTrue(rendered.contains("Prepared technical questions: 3"));
        assertFalse(rendered.contains("HIDDEN_QUESTION_PROMPT"));
        assertFalse(rendered.contains("HIDDEN_EXPECTED_KEY_POINT"));
    }

    @Test
    void invalidModelResponseReturnsTheDocumentedExitCodeWithoutAStackTrace() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(
                new CountingAnalyzer(new InvalidCodexResponseException("Invalid project analysis.")),
                new CountingConfirmation(true),
                new ByteArrayOutputStream(),
                error);

        assertEquals(ExitCodes.INVALID_MODEL_RESPONSE, commandLine.execute("--yes", root.toString()));
        String renderedError = error.toString(StandardCharsets.UTF_8);
        assertTrue(renderedError.contains("Invalid project analysis."));
        assertNoStackTrace(error);
    }

    @Test
    void timeoutAndExecutionFailuresRemainExitCodeSeven() throws Exception {
        ByteArrayOutputStream timeoutError = new ByteArrayOutputStream();
        CommandLine timeout = commandLine(
                new CountingAnalyzer(new CodexTimeoutException()),
                new CountingConfirmation(true),
                new ByteArrayOutputStream(),
                timeoutError);
        ByteArrayOutputStream executionError = new ByteArrayOutputStream();
        CommandLine execution = commandLine(
                new CountingAnalyzer(new CodexExecutionException(9, "safe diagnostic")),
                new CountingConfirmation(true),
                new ByteArrayOutputStream(),
                executionError);

        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, timeout.execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, execution.execute("--yes", root.toString()));
        assertNoStackTrace(timeoutError);
        assertNoStackTrace(executionError);
    }

    @Test
    void unavailableAnalysisResourcesReturnExitCodeSevenWithoutAStackTrace() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(
                new CountingAnalyzer(new CodexExecutionException(
                        -1, "Project analysis resources are unavailable.")),
                new CountingConfirmation(true),
                new ByteArrayOutputStream(),
                error);

        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, commandLine.execute("--yes", root.toString()));
        String renderedError = error.toString(StandardCharsets.UTF_8);
        assertTrue(renderedError.contains("Project analysis resources are unavailable."));
        assertNoStackTrace(error);
    }

    @Test
    void missingCodexRemainsExitCodeFive() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(
                new CountingAnalyzer(new CodexNotInstalledException()),
                new CountingConfirmation(true),
                new ByteArrayOutputStream(),
                error);

        assertEquals(ExitCodes.CODEX_NOT_INSTALLED, commandLine.execute("--yes", root.toString()));
        assertNoStackTrace(error);
    }

    @Test
    void missingAuthenticationRemainsExitCodeSix() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(
                new CountingAnalyzer(new CodexNotAuthenticatedException()),
                new CountingConfirmation(true),
                new ByteArrayOutputStream(),
                error);

        assertEquals(ExitCodes.CODEX_NOT_AUTHENTICATED, commandLine.execute("--yes", root.toString()));
        assertNoStackTrace(error);
    }

    @Test
    void interruptionRemainsExitCodeOneHundredThirty() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(
                new CountingAnalyzer(new CodexInterruptedException(new InterruptedException("interrupted"))),
                new CountingConfirmation(true),
                new ByteArrayOutputStream(),
                error);

        try {
            assertEquals(ExitCodes.CANCELLED, commandLine.execute("--yes", root.toString()));
            assertTrue(Thread.currentThread().isInterrupted());
            assertNoStackTrace(error);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void helpAndVersionDoNotCallTheAnalyzerAndYesWarnsAboutCredits() throws Exception {
        CountingAnalyzer analyzer = new CountingAnalyzer(analysis());
        CommandLine commandLine = commandLine(
                analyzer, new CountingConfirmation(true), new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--help"));
        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--version"));
        assertEquals(0, analyzer.calls);
        assertTrue(commandLine.getUsageMessage().contains("consume Codex credits"));
    }

    private CommandLine commandLine(
            ProjectAnalyzer analyzer,
            ConfirmationPrompt confirmation,
            ByteArrayOutputStream output,
            ByteArrayOutputStream error) throws Exception {
        var runner = new DefaultProjectDefenseRunner(
                validScanner(),
                new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()),
                confirmation,
                new ProjectAnalysisRenderer(),
                ignored -> new CodeDefenseRuntime(analyzer, analysis -> null,
                        (snapshot, analysis, session) -> {
                            throw new AssertionError("No report is expected without an interview session");
                        }));
        CommandLine commandLine = new CommandLine(new StartCommand(runner));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));
        return commandLine;
    }

    private ProjectScanner validScanner() throws Exception {
        Path file = root.resolve("App.java");
        Files.writeString(file, "class App {}");
        long fileSize = Files.size(file);
        return (path, policy) -> new ScanSummary(
                root, 1, 0, List.of(new SourceFile(file.getFileName(), fileSize)));
    }

    private static void assertNoStackTrace(ByteArrayOutputStream error) {
        assertFalse(error.toString(StandardCharsets.UTF_8).contains("\tat "));
    }

    private static ProjectAnalysis analysis() {
        return new ProjectAnalysis(
                "Fixture project",
                "Java CLI",
                "A focused fixture application.",
                List.of("The command parses arguments.", "The application validates input."),
                List.of(new ProjectComponent(
                        "Command", "entry-point", "Parses command-line arguments.", List.of("App.java"))),
                List.of("startup", "validation"),
                List.of(question("startup"), question("flow"), question("validation")));
    }

    private static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(
                id,
                "HIDDEN_QUESTION_PROMPT How does " + id + " work?",
                "Understand " + id,
                List.of("HIDDEN_EXPECTED_KEY_POINT", "Second expected point"),
                List.of(new CodeEvidence("App.java", 1, 1, "Fixture evidence.")));
    }

    private static final class CountingAnalyzer implements ProjectAnalyzer {
        private final ProjectAnalysis result;
        private final RuntimeException failure;
        private int calls;

        private CountingAnalyzer(ProjectAnalysis result) {
            this.result = result;
            this.failure = null;
        }

        private CountingAnalyzer(RuntimeException failure) {
            this.result = null;
            this.failure = failure;
        }

        @Override
        public ProjectAnalysis analyze(dev.codedefense.domain.ProjectSnapshot snapshot) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class CountingConfirmation implements ConfirmationPrompt {
        private final boolean answer;
        private int calls;

        private CountingConfirmation(boolean answer) {
            this.answer = answer;
        }

        @Override
        public boolean confirm(String prompt) {
            calls++;
            return answer;
        }
    }
}
