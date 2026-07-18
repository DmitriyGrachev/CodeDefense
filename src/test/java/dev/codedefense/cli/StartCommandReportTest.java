package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.analysis.ProjectAnalyzer;
import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.application.CodeDefenseRuntime;
import dev.codedefense.application.DefaultProjectDefenseRunner;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.NarrativeSource;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.Readiness;
import dev.codedefense.domain.SavedReport;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.TurnType;
import dev.codedefense.domain.Verdict;
import dev.codedefense.interview.InterviewCancelledException;
import dev.codedefense.interview.InterviewRunner;
import dev.codedefense.report.ReportPersistenceException;
import dev.codedefense.report.ReportService;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.terminal.ProjectAnalysisRenderer;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StartCommandReportTest {
    @TempDir Path root;

    @Test
    void completedInterviewGeneratesAndSavesReportWithTheSameWorkflowInstances() throws Exception {
        ProjectAnalysis analysis = analysis();
        InterviewSession session = session(analysis);
        CapturingReportService reports = new CapturingReportService(
                new SavedReport(root.resolve("report.md").toAbsolutePath(), NarrativeSource.AI));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicReference<dev.codedefense.domain.ProjectSnapshot> analyzedSnapshot = new AtomicReference<>();

        assertEquals(ExitCodes.SUCCESS, commandLine(snapshot -> { analyzedSnapshot.set(snapshot); return analysis; }, ignored -> session, reports, output).execute("--yes", root.toString()));

        assertEquals(1, reports.calls.get());
        assertSame(analyzedSnapshot.get(), reports.snapshot);
        assertSame(analysis, reports.analysis);
        assertSame(session, reports.session);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Generating understanding report..."));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Understanding report saved: " + root.resolve("report.md").toAbsolutePath()));
    }

    @Test
    void fallbackReportWarnsBeforeTheSavedPath() throws Exception {
        ProjectAnalysis analysis = analysis();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SavedReport saved = new SavedReport(root.resolve("fallback.md").toAbsolutePath(), NarrativeSource.DETERMINISTIC_FALLBACK);

        assertEquals(ExitCodes.SUCCESS, commandLine(analysis, ignored -> session(analysis), new CapturingReportService(saved), output)
                .execute("--yes", root.toString()));

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Codex report narrative was unavailable; saved a deterministic fallback report."));
        assertTrue(text.indexOf("deterministic fallback") < text.indexOf("Understanding report saved:"));
    }

    @Test
    void persistenceFailureMapsToExitNineWithoutAStackTrace() throws Exception {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(analysis(), ignored -> session(analysis()), (snapshot, analysis, session) -> {
            throw ReportPersistenceException.saveFailure();
        }, new ByteArrayOutputStream());
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));

        assertEquals(ExitCodes.REPORT_PERSISTENCE_FAILED, commandLine.execute("--yes", root.toString()));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains(ReportPersistenceException.SAVE_FAILURE_MESSAGE));
        assertFalse(error.toString(StandardCharsets.UTF_8).contains("\tat "));
    }

    @Test
    void allPreReportFailureAndCancellationPathsDoNotGenerateReports() throws Exception {
        CapturingReportService reports = new CapturingReportService(new SavedReport(root.resolve("never.md").toAbsolutePath(), NarrativeSource.AI));
        assertEquals(ExitCodes.SUCCESS, commandLine(analysis(), ignored -> session(analysis()), reports, new ByteArrayOutputStream())
                .execute("--dry-run", root.toString()));
        assertEquals(ExitCodes.SUCCESS, commandLine(analysis(), ignored -> session(analysis()), reports, new ByteArrayOutputStream(), false)
                .execute(root.toString()));
        assertEquals(ExitCodes.INVALID_PROJECT_PATH, commandLine((root, policy) -> { throw new dev.codedefense.scanner.InvalidProjectPathException("bad path"); },
                snapshot -> analysis(), ignored -> session(analysis()), reports).execute("--yes", root.toString()));
        assertEquals(ExitCodes.NO_SUPPORTED_SOURCE_FILES, commandLine((root, policy) -> { throw new dev.codedefense.scanner.NoSupportedSourceFilesException("empty"); },
                snapshot -> analysis(), ignored -> session(analysis()), reports).execute("--yes", root.toString()));
        assertEquals(ExitCodes.NO_SUPPORTED_SOURCE_FILES, commandLine((root, policy) -> new ScanSummary(root, 1, 0, List.of(new SourceFile(Path.of("missing.java"), 1))),
                snapshot -> analysis(), ignored -> session(analysis()), reports).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, commandLine(snapshot -> { throw new dev.codedefense.ai.exception.CodexExecutionException(1, "analysis failed"); },
                ignored -> session(analysis()), reports, new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.INVALID_MODEL_RESPONSE, commandLine(snapshot -> { throw new dev.codedefense.ai.exception.InvalidCodexResponseException("invalid analysis"); },
                ignored -> session(analysis()), reports, new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_NOT_INSTALLED, commandLine(snapshot -> { throw new dev.codedefense.ai.exception.CodexNotInstalledException(); },
                ignored -> session(analysis()), reports, new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_NOT_AUTHENTICATED, commandLine(snapshot -> { throw new dev.codedefense.ai.exception.CodexNotAuthenticatedException(); },
                ignored -> session(analysis()), reports, new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, commandLine(snapshot -> { throw new dev.codedefense.ai.exception.CodexTimeoutException(); },
                ignored -> session(analysis()), reports, new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CANCELLED, commandLine(snapshot -> { throw new dev.codedefense.ai.exception.CodexInterruptedException(new InterruptedException("interrupted")); },
                ignored -> session(analysis()), reports, new ByteArrayOutputStream()).execute("--yes", root.toString()));
        Thread.interrupted();
        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, commandLine(analysis(), ignored -> { throw new dev.codedefense.ai.exception.CodexExecutionException(1, "evaluation failed"); }, reports,
                new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.INVALID_MODEL_RESPONSE, commandLine(analysis(), ignored -> { throw new dev.codedefense.ai.exception.InvalidCodexResponseException("invalid evaluation"); }, reports,
                new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_NOT_INSTALLED, commandLine(analysis(), ignored -> { throw new dev.codedefense.ai.exception.CodexNotInstalledException(); }, reports,
                new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_NOT_AUTHENTICATED, commandLine(analysis(), ignored -> { throw new dev.codedefense.ai.exception.CodexNotAuthenticatedException(); }, reports,
                new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED, commandLine(analysis(), ignored -> { throw new dev.codedefense.ai.exception.CodexTimeoutException(); }, reports,
                new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(ExitCodes.CANCELLED, commandLine(analysis(), ignored -> { throw new dev.codedefense.ai.exception.CodexInterruptedException(new InterruptedException("interrupted")); }, reports,
                new ByteArrayOutputStream()).execute("--yes", root.toString()));
        Thread.interrupted();
        assertEquals(ExitCodes.CANCELLED, commandLine(analysis(), ignored -> { throw new InterviewCancelledException("cancelled"); }, reports,
                new ByteArrayOutputStream()).execute("--yes", root.toString()));
        assertEquals(0, reports.calls.get());
    }

    private CommandLine commandLine(ProjectAnalysis analysis, InterviewRunner runner, ReportService reports, ByteArrayOutputStream output) throws Exception {
        return commandLine(snapshot -> analysis, runner, reports, output);
    }

    private CommandLine commandLine(ProjectAnalysis analysis, InterviewRunner runner, ReportService reports, ByteArrayOutputStream output, boolean confirmation) throws Exception {
        return commandLine(snapshot -> analysis, runner, reports, output, confirmation);
    }

    private CommandLine commandLine(ProjectAnalyzer analyzer, InterviewRunner runner, ReportService reports, ByteArrayOutputStream output) throws Exception {
        return commandLine(analyzer, runner, reports, output, true);
    }

    private CommandLine commandLine(ProjectAnalyzer analyzer, InterviewRunner runner, ReportService reports, ByteArrayOutputStream output, boolean confirmation) throws Exception {
        Path file = root.resolve("App.java");
        Files.writeString(file, "class App {}");
        long fileSize = Files.size(file);
        ProjectScanner scanner = (path, policy) -> new ScanSummary(root, 1, 0, List.of(new SourceFile(Path.of("App.java"), fileSize)));
        var workflow = new DefaultProjectDefenseRunner(scanner, new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()),
                prompt -> confirmation, new ProjectAnalysisRenderer(),
                ignored -> new CodeDefenseRuntime(analyzer, runner, reports));
        CommandLine line = new CommandLine(new StartCommand(workflow));
        line.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        return line;
    }

    private CommandLine commandLine(ProjectScanner scanner, ProjectAnalyzer analyzer, InterviewRunner runner, ReportService reports) {
        var workflow = new DefaultProjectDefenseRunner(scanner, new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()),
                prompt -> true, new ProjectAnalysisRenderer(),
                ignored -> new CodeDefenseRuntime(analyzer, runner, reports));
        CommandLine line = new CommandLine(new StartCommand(workflow));
        line.setOut(new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        return line;
    }

    private static ProjectAnalysis analysis() {
        TechnicalQuestion question = new TechnicalQuestion("q1", "How does it work?", "Goal", List.of("key", "detail"), List.of(new CodeEvidence("App.java", 1, 1, "reason")));
        return new ProjectAnalysis("Demo", "Java", "A valid project summary.", List.of("entry flow", "exit flow"),
                List.of(new ProjectComponent("App", "class", "Entry point", List.of("App.java"))), List.of("failure", "testing"), List.of(question,
                new TechnicalQuestion("q2", "What fails?", "Goal", List.of("key", "detail"), List.of(new CodeEvidence("App.java", 1, 1, "reason"))),
                new TechnicalQuestion("q3", "How is it tested?", "Goal", List.of("key", "detail"), List.of(new CodeEvidence("App.java", 1, 1, "reason")))));
    }

    private static InterviewSession session(ProjectAnalysis analysis) {
        return new InterviewSession("Demo", List.of(result(analysis.questions().get(0), 1), result(analysis.questions().get(1), 2), result(analysis.questions().get(2), 3)), 80, Readiness.STRONG_UNDERSTANDING, 0);
    }

    private static QuestionResult result(TechnicalQuestion question, int number) {
        AnswerEvaluation evaluation = new AnswerEvaluation(Verdict.CORRECT, 80, "Good", List.of("key"), List.of(), Optional.empty());
        return new QuestionResult(number, question, new InterviewTurn(TurnType.PRIMARY, question.prompt(), "answer", evaluation), Optional.empty(), 80);
    }

    private static final class CapturingReportService implements ReportService {
        private final SavedReport saved; private final AtomicInteger calls = new AtomicInteger();
        private dev.codedefense.domain.ProjectSnapshot snapshot; private ProjectAnalysis analysis; private InterviewSession session;
        private CapturingReportService(SavedReport saved) { this.saved = saved; }
        @Override public SavedReport generateAndSave(dev.codedefense.domain.ProjectSnapshot snapshot, ProjectAnalysis analysis, InterviewSession session) {
            calls.incrementAndGet(); this.snapshot = snapshot; this.analysis = analysis; this.session = session; return saved;
        }
    }
}
