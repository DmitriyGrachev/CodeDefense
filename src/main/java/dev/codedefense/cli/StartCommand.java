package dev.codedefense.cli;

import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.analysis.ProjectAnalyzer;
import dev.codedefense.analysis.ProjectAnalysisRuntimeFactory;
import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.scanner.FileSystemProjectScanner;
import dev.codedefense.scanner.InvalidProjectPathException;
import dev.codedefense.scanner.NoSupportedSourceFilesException;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.scanner.ScanPolicy;
import dev.codedefense.terminal.ConfirmationPrompt;
import dev.codedefense.terminal.ConsoleConfirmationPrompt;
import dev.codedefense.terminal.ProjectAnalysisRenderer;
import java.nio.file.Path;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "start",
        mixinStandardHelpOptions = true,
        description = "Start a technical defense for a local repository.")
public final class StartCommand implements java.util.concurrent.Callable<Integer> {
    private final ProjectScanner scanner;
    private final ProjectSnapshotBuilder snapshotBuilder;
    private final ConfirmationPrompt confirmation;
    private final ProjectAnalyzer analyzer;
    private final ProjectAnalysisRenderer analysisRenderer;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH", description = "Repository path (default: current directory).")
    private Path path;

    @Option(names = "--dry-run", description = "Discover supported files without starting a defense.")
    private boolean dryRun;
    @Option(names = {"-y", "--yes"}, description = "Skip confirmation; analysis can consume Codex credits.") private boolean yes;

    @Spec
    private CommandSpec commandSpec;

    public StartCommand() {
        this(
                new FileSystemProjectScanner(),
                new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()),
                new ConsoleConfirmationPrompt(),
                new ProjectAnalysisRuntimeFactory().create(),
                new ProjectAnalysisRenderer());
    }

    StartCommand(ProjectScanner scanner) {
        this(scanner, new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), prompt -> false,
                new ProjectAnalysisRuntimeFactory().create(), new ProjectAnalysisRenderer());
    }

    StartCommand(
            ProjectScanner scanner,
            ProjectSnapshotBuilder snapshotBuilder,
            ConfirmationPrompt confirmation,
            ProjectAnalyzer analyzer,
            ProjectAnalysisRenderer analysisRenderer) {
        this.scanner = Objects.requireNonNull(scanner, "Project scanner");
        this.snapshotBuilder = Objects.requireNonNull(snapshotBuilder, "Project snapshot builder");
        this.confirmation = Objects.requireNonNull(confirmation, "Confirmation prompt");
        this.analyzer = Objects.requireNonNull(analyzer, "Project analyzer");
        this.analysisRenderer = Objects.requireNonNull(analysisRenderer, "Project analysis renderer");
    }

    public Path path() {
        return path;
    }

    @Override
    public Integer call() {
        try {
            ScanSummary summary = scanner.scan(path, ScanPolicy.defaults());
            var snapshot = snapshotBuilder.build(summary);
            commandSpec.commandLine().getOut().printf(
                    "Project: %s%nDetected type: %s%nDiscovered files: %d%nIgnored files: %d%nAccepted candidates: %d%nSelected files: %d / %d%nSnapshot bytes: %d / %d%nTruncated files: %d%nRedactions: %d%n",
                    snapshot.projectName(), snapshot.projectType(),
                    summary.discoveredFileCount(),
                    summary.ignoredFileCount(),
                    summary.acceptedCandidateCount(), snapshot.selectedFiles().size(), snapshotBuilder.config().maximumSelectedFiles(), snapshot.promptBytes(), snapshotBuilder.config().maximumSnapshotBytes(), snapshot.selectedFiles().stream().filter(file -> file.truncated()).count(), snapshot.redactionCount()
            );
            snapshot.selectedFiles().forEach(file -> commandSpec.commandLine().getOut().println(file.relativePath() + " (" + file.renderedBytes() + " bytes)"));
            if (dryRun) { commandSpec.commandLine().getOut().println("No source content was sent."); commandSpec.commandLine().getOut().println("Codex was not invoked."); return ExitCodes.SUCCESS; }
            String prompt = "Send selected snapshot to Codex? [y/N]";
            if (!yes) commandSpec.commandLine().getOut().println(prompt);
            if (!yes && !confirmation.confirm(prompt)) { commandSpec.commandLine().getOut().println("Cancelled before any source content was sent."); return ExitCodes.SUCCESS; }
            commandSpec.commandLine().getOut().println("Analyzing project with GPT-5.6...");
            ProjectAnalysis analysis = analyzer.analyze(snapshot);
            analysisRenderer.render(analysis, commandSpec.commandLine().getOut());
            return ExitCodes.SUCCESS;
        } catch (InvalidProjectPathException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.INVALID_PROJECT_PATH;
        } catch (NoSupportedSourceFilesException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        } catch (EmptyProjectSnapshotException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        } catch (InvalidCodexResponseException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.INVALID_MODEL_RESPONSE;
        } catch (CodexNotInstalledException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.CODEX_NOT_INSTALLED;
        } catch (CodexNotAuthenticatedException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.CODEX_NOT_AUTHENTICATED;
        } catch (CodexTimeoutException | CodexExecutionException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.CODEX_EXECUTION_FAILED;
        } catch (CodexInterruptedException exception) {
            Thread.currentThread().interrupt();
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.CANCELLED;
        }
    }

}
