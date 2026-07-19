package dev.codedefense.application;

import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.ai.exception.*;
import dev.codedefense.analysis.AiGitChangeAnalyzer;
import dev.codedefense.change.*;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.DefenseFocus;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.interview.InterviewCancelledException;
import dev.codedefense.passport.*;
import dev.codedefense.terminal.ConfirmationPrompt;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** One workflow for staged, commit, and merge-base range defenses. */
public final class DefaultGitChangeDefenseRunner implements GitChangeDefenseRunner {
    private final GitChangeSource source;
    private final GitChangeContextBuilder contextBuilder;
    private final GitChangePreviewRenderer previewRenderer;
    private final Supplier<ConfirmationPrompt> confirmationFactory;
    private final CodeDefenseRuntimeProvider runtimeProvider;
    private final Supplier<GitChangePassportService> passportServiceFactory;

    public DefaultGitChangeDefenseRunner(GitChangeSource source, GitChangeContextBuilder contextBuilder,
            GitChangePreviewRenderer previewRenderer, Supplier<ConfirmationPrompt> confirmationFactory,
            CodeDefenseRuntimeProvider runtimeProvider, Supplier<GitChangePassportService> passportServiceFactory) {
        this.source = Objects.requireNonNull(source); this.contextBuilder = Objects.requireNonNull(contextBuilder);
        this.previewRenderer = Objects.requireNonNull(previewRenderer);
        this.confirmationFactory = Objects.requireNonNull(confirmationFactory);
        this.runtimeProvider = Objects.requireNonNull(runtimeProvider);
        this.passportServiceFactory = Objects.requireNonNull(passportServiceFactory);
    }

    public static DefaultGitChangeDefenseRunner production(Supplier<ConfirmationPrompt> confirmationFactory) {
        GitChangeSource source = new GitCliChangeSource(new JdkProcessExecutor());
        return new DefaultGitChangeDefenseRunner(source, new GitChangeContextBuilder(),
                new GitChangePreviewRenderer(), confirmationFactory, new CodeDefenseRuntimeFactory(),
                () -> new GitChangePassportService(source,
                        new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                                new MarkdownChangePassportRenderer(), Clock.systemUTC()),
                        Clock.systemUTC(), CodexRuntimeConfig.defaults().defaultModel()));
    }

    @Override public int run(Path repositoryPath, ChangeSelector selector, boolean dryRun,
            boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        return run(repositoryPath, selector, DefenseFocus.BALANCED, dryRun, skipConfirmation, out, err);
    }

    @Override public int run(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        try {
            CapturedGitChange captured = source.capture(repositoryPath, selector);
            var snapshot = contextBuilder.build(captured);
            previewRenderer.render(captured.change(), snapshot, out);
            out.println("Focus: " + focus.displayName());
            if (dryRun) {
                out.println("No source content was sent.");
                out.println("Codex was not invoked.");
                return ExitCodes.SUCCESS;
            }
            String prompt = "Send bounded Git change context to Codex? [y/N]";
            if (!skipConfirmation) {
                out.println(prompt);
                if (!confirmationFactory.get().confirm(prompt)) {
                    out.println("Cancelled before any source content was sent.");
                    return ExitCodes.SUCCESS;
                }
            }
            CodeDefenseRuntime runtime = runtimeProvider.create(out);
            var analysis = new AiGitChangeAnalyzer(runtime.stagedChangeAnalyzer())
                    .analyze(captured.change(), snapshot, focus);
            var session = runtime.interviewRunner().conduct(analysis);
            if (session != null) {
                SavedChangePassport saved = passportServiceFactory.get()
                        .createAndSave(selector, captured.change(), analysis, session, focus);
                out.println("Change Passport saved: " + saved.path().getFileName());
                out.println("Status: " + saved.status());
                out.println("Overall score: " + session.overallScore() + "/100");
                out.println("Fingerprint: " + captured.change().diffFingerprint().substring(0, 12));
                out.println("Next:");
                out.println("  codedefense passport show .");
                out.println("  codedefense passport verify .");
                out.println("  codedefense passport export . --format json --output passport.json");
            }
            return ExitCodes.SUCCESS;
        } catch (GitChangeException exception) {
            return renderGitFailure(exception, err);
        } catch (EmptyProjectSnapshotException exception) {
            err.println("No eligible changed source files were found."); return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        } catch (InvalidCodexResponseException exception) {
            err.println(exception.getMessage()); return ExitCodes.INVALID_MODEL_RESPONSE;
        } catch (CodexNotInstalledException exception) {
            err.println(exception.getMessage()); return ExitCodes.CODEX_NOT_INSTALLED;
        } catch (CodexNotAuthenticatedException exception) {
            err.println(exception.getMessage()); return ExitCodes.CODEX_NOT_AUTHENTICATED;
        } catch (CodexTimeoutException | CodexExecutionException exception) {
            err.println(exception.getMessage()); return ExitCodes.CODEX_EXECUTION_FAILED;
        } catch (CodexInterruptedException | InterviewCancelledException exception) {
            Thread.currentThread().interrupt(); err.println(exception.getMessage()); return ExitCodes.CANCELLED;
        } catch (ChangePassportPersistenceException exception) {
            err.println(exception.getMessage()); return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }

    private static int renderGitFailure(GitChangeException exception, PrintWriter err) {
        if (exception.kind() == GitChangeException.Kind.INVALID_REPOSITORY) {
            err.println("The supplied path is not a readable Git repository."); return ExitCodes.INVALID_PROJECT_PATH;
        }
        if (exception.kind() == GitChangeException.Kind.NO_STAGED_CHANGE) {
            err.println("No eligible staged source files were found."); return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        }
        err.println(exception.getMessage()); return ExitCodes.GIT_EXECUTION_FAILED;
    }
}
