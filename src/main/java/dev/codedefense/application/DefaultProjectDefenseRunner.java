package dev.codedefense.application;

import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.interview.InterviewCancelledException;
import dev.codedefense.report.ReportPersistenceException;
import dev.codedefense.scanner.FileSystemProjectScanner;
import dev.codedefense.scanner.InvalidProjectPathException;
import dev.codedefense.scanner.NoSupportedSourceFilesException;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.scanner.ScanPolicy;
import dev.codedefense.terminal.ConfirmationPrompt;
import dev.codedefense.terminal.ConsoleConfirmationPrompt;
import dev.codedefense.terminal.ProjectAnalysisRenderer;
import dev.codedefense.terminal.TerminalTextSanitizer;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;

/** Default orchestration shared by the {@code start} and {@code sample} commands. */
public final class DefaultProjectDefenseRunner implements ProjectDefenseRunner {
    private final ProjectScanner scanner;
    private final ProjectSnapshotBuilder snapshotBuilder;
    private final ConfirmationPrompt confirmation;
    private final ProjectAnalysisRenderer analysisRenderer;
    private final CodeDefenseRuntimeProvider runtimeProvider;

    public DefaultProjectDefenseRunner(ProjectScanner scanner, ProjectSnapshotBuilder snapshotBuilder,
            ConfirmationPrompt confirmation, ProjectAnalysisRenderer analysisRenderer,
            CodeDefenseRuntimeProvider runtimeProvider) {
        this.scanner = Objects.requireNonNull(scanner, "Project scanner");
        this.snapshotBuilder = Objects.requireNonNull(snapshotBuilder, "Project snapshot builder");
        this.confirmation = Objects.requireNonNull(confirmation, "Confirmation prompt");
        this.analysisRenderer = Objects.requireNonNull(analysisRenderer, "Project analysis renderer");
        this.runtimeProvider = Objects.requireNonNull(runtimeProvider, "CodeDefense runtime provider");
    }

    public static DefaultProjectDefenseRunner production() {
        return new DefaultProjectDefenseRunner(
                new FileSystemProjectScanner(),
                new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()),
                new ConsoleConfirmationPrompt(),
                new ProjectAnalysisRenderer(),
                new CodeDefenseRuntimeFactory());
    }

    @Override
    public int run(Path projectPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(projectPath, "Project path");
        Objects.requireNonNull(out, "Command output");
        Objects.requireNonNull(err, "Command error output");
        try {
            ScanSummary summary = scanner.scan(projectPath, ScanPolicy.defaults());
            var snapshot = snapshotBuilder.build(summary);
            renderPreview(summary, snapshot, out);
            if (dryRun) {
                out.println("No source content was sent.");
                out.println("Codex was not invoked.");
                return ExitCodes.SUCCESS;
            }

            String prompt = "Send selected snapshot to Codex? [y/N]";
            if (!skipConfirmation) {
                out.println(prompt);
            }
            if (!skipConfirmation && !confirmation.confirm(prompt)) {
                out.println("Cancelled before any source content was sent.");
                return ExitCodes.SUCCESS;
            }

            CodeDefenseRuntime runtime = runtimeProvider.create(out);
            out.println("Analyzing project with GPT-5.6...");
            ProjectAnalysis analysis = runtime.analyzer().analyze(snapshot);
            analysisRenderer.render(analysis, out);
            var session = runtime.interviewRunner().conduct(analysis);
            if (session != null) {
                out.println("Generating understanding report...");
                var savedReport = runtime.reportService().generateAndSave(snapshot, analysis, session);
                if (savedReport.narrativeSource() == dev.codedefense.domain.NarrativeSource.DETERMINISTIC_FALLBACK) {
                    out.println("Codex report narrative was unavailable; saved a deterministic fallback report.");
                }
                out.println("Understanding report saved: "
                        + TerminalTextSanitizer.singleLine(savedReport.path().toString()));
            }
            return ExitCodes.SUCCESS;
        } catch (InvalidProjectPathException exception) {
            err.println(exception.getMessage());
            return ExitCodes.INVALID_PROJECT_PATH;
        } catch (NoSupportedSourceFilesException | EmptyProjectSnapshotException exception) {
            err.println(exception.getMessage());
            return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        } catch (InvalidCodexResponseException exception) {
            err.println(exception.getMessage());
            return ExitCodes.INVALID_MODEL_RESPONSE;
        } catch (CodexNotInstalledException exception) {
            err.println(exception.getMessage());
            return ExitCodes.CODEX_NOT_INSTALLED;
        } catch (CodexNotAuthenticatedException exception) {
            err.println(exception.getMessage());
            return ExitCodes.CODEX_NOT_AUTHENTICATED;
        } catch (CodexTimeoutException | CodexExecutionException exception) {
            err.println(exception.getMessage());
            return ExitCodes.CODEX_EXECUTION_FAILED;
        } catch (CodexInterruptedException exception) {
            Thread.currentThread().interrupt();
            err.println(exception.getMessage());
            return ExitCodes.CANCELLED;
        } catch (InterviewCancelledException exception) {
            err.println(exception.getMessage());
            return ExitCodes.CANCELLED;
        } catch (ReportPersistenceException exception) {
            err.println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }

    private void renderPreview(ScanSummary summary, dev.codedefense.domain.ProjectSnapshot snapshot, PrintWriter out) {
        out.printf(
                "Project: %s%nDetected type: %s%nDiscovered files: %d%nIgnored files: %d%nAccepted candidates: %d%nSelected files: %d / %d%nSnapshot bytes: %d / %d%nTruncated files: %d%nRedactions: %d%n",
                snapshot.projectName(), snapshot.projectType(),
                summary.discoveredFileCount(),
                summary.ignoredFileCount(),
                summary.acceptedCandidateCount(), snapshot.selectedFiles().size(),
                snapshotBuilder.config().maximumSelectedFiles(), snapshot.promptBytes(),
                snapshotBuilder.config().maximumSnapshotBytes(),
                snapshot.selectedFiles().stream().filter(file -> file.truncated()).count(),
                snapshot.redactionCount());
        snapshot.selectedFiles().forEach(file -> out.println(file.relativePath() + " (" + file.renderedBytes() + " bytes)"));
    }
}
