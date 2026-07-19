package dev.codedefense.application;

import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.GitCliStagedChangeSource;
import dev.codedefense.change.StagedChangeContextBuilder;
import dev.codedefense.change.StagedChangePreviewRenderer;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.interview.InterviewCancelledException;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import dev.codedefense.terminal.ConfirmationPrompt;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** Orchestrates a proof strictly from the staged Git index. */
public final class DefaultStagedChangeDefenseRunner implements StagedChangeDefenseRunner {
    private final StagedChangeSource source;
    private final StagedChangeContextBuilder contextBuilder;
    private final StagedChangePreviewRenderer previewRenderer;
    private final Supplier<ConfirmationPrompt> confirmationFactory;
    private final CodeDefenseRuntimeProvider runtimeProvider;
    private final Supplier<ChangePassportService> passportServiceFactory;

    public DefaultStagedChangeDefenseRunner(StagedChangeSource source,
            StagedChangeContextBuilder contextBuilder,
            StagedChangePreviewRenderer previewRenderer,
            Supplier<ConfirmationPrompt> confirmationFactory,
            CodeDefenseRuntimeProvider runtimeProvider,
            Supplier<ChangePassportService> passportServiceFactory) {
        this.source = Objects.requireNonNull(source, "Staged change source");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "Staged context builder");
        this.previewRenderer = Objects.requireNonNull(previewRenderer, "Staged preview renderer");
        this.confirmationFactory = Objects.requireNonNull(confirmationFactory, "Confirmation prompt factory");
        this.runtimeProvider = Objects.requireNonNull(runtimeProvider, "Runtime provider");
        this.passportServiceFactory = Objects.requireNonNull(passportServiceFactory, "Passport service factory");
    }

    public static DefaultStagedChangeDefenseRunner production(Supplier<ConfirmationPrompt> confirmationFactory) {
        StagedChangeSource source = new GitCliStagedChangeSource(new JdkProcessExecutor());
        return new DefaultStagedChangeDefenseRunner(
                source,
                new StagedChangeContextBuilder(),
                new StagedChangePreviewRenderer(),
                confirmationFactory,
                new CodeDefenseRuntimeFactory(),
                () -> new ChangePassportService(source,
                        new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                                new MarkdownChangePassportRenderer(), Clock.systemUTC()),
                        Clock.systemUTC(), CodexRuntimeConfig.defaults().defaultModel()));
    }

    @Override
    public int run(Path repositoryPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(repositoryPath, "Repository path");
        Objects.requireNonNull(out, "Command output");
        Objects.requireNonNull(err, "Command error output");
        try {
            CapturedStagedChange captured = source.capture(repositoryPath);
            var snapshot = contextBuilder.build(captured);
            previewRenderer.render(captured.change(), snapshot, out);
            if (dryRun) {
                out.println("No source content was sent.");
                out.println("Codex was not invoked.");
                return ExitCodes.SUCCESS;
            }

            String prompt = "Send bounded staged change context to Codex? [y/N]";
            if (!skipConfirmation) {
                out.println(prompt);
                if (!Objects.requireNonNull(confirmationFactory.get(), "Confirmation prompt").confirm(prompt)) {
                    out.println("Cancelled before any source content was sent.");
                    return ExitCodes.SUCCESS;
                }
            }

            CodeDefenseRuntime runtime = runtimeProvider.create(out);
            ProjectAnalysis analysis = runtime.stagedChangeAnalyzer().analyze(captured.change(), snapshot);
            var session = runtime.interviewRunner().conduct(analysis);
            if (session != null) {
                Path saved = passportServiceFactory.get().createAndSave(captured.change(), analysis, session);
                out.println("Change Passport saved: " + saved.getFileName());
            }
            return ExitCodes.SUCCESS;
        } catch (GitChangeException exception) {
            return renderGitFailure(exception, err);
        } catch (EmptyProjectSnapshotException exception) {
            err.println("No eligible staged source files were found.");
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
        } catch (ChangePassportPersistenceException exception) {
            err.println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }

    private static int renderGitFailure(GitChangeException exception, PrintWriter err) {
        return switch (exception.kind()) {
            case INVALID_REPOSITORY -> {
                err.println("The supplied path is not a readable Git repository.");
                yield ExitCodes.INVALID_PROJECT_PATH;
            }
            case NO_STAGED_CHANGE -> {
                err.println("No eligible staged source files were found.");
                yield ExitCodes.NO_SUPPORTED_SOURCE_FILES;
            }
            case CHANGED_DURING_CAPTURE -> {
                err.println("Staged change changed during capture; retry.");
                yield ExitCodes.GIT_EXECUTION_FAILED;
            }
            case NO_HEAD, EXECUTION_FAILED, MALFORMED_DATA -> {
                err.println("Git could not safely capture the staged change.");
                yield ExitCodes.GIT_EXECUTION_FAILED;
            }
        };
    }
}
