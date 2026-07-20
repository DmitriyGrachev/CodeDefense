package dev.codedefense.application;

import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.ai.exception.*;
import dev.codedefense.analysis.AiGitChangeAnalyzer;
import dev.codedefense.change.*;
import dev.codedefense.bridge.BridgeConfirmationPrompt;
import dev.codedefense.bridge.BridgeEvent;
import dev.codedefense.bridge.BridgeInterviewInput;
import dev.codedefense.bridge.BridgeInterviewOutput;
import dev.codedefense.bridge.BridgeProtocol;
import dev.codedefense.bridge.BridgeSession;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.DefenseFocus;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.CodexProvenanceSummary;
import dev.codedefense.domain.EvidenceCoverageMap;
import dev.codedefense.interview.InterviewCancelledException;
import dev.codedefense.passport.*;
import dev.codedefense.provenance.*;
import dev.codedefense.terminal.ConfirmationPrompt;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** One workflow for staged, commit, and merge-base range defenses. */
public final class DefaultGitChangeDefenseRunner implements GitChangeDefenseRunner {
    private final GitChangeSource source;
    private final GitChangeContextBuilder contextBuilder;
    private final GitChangePreviewRenderer previewRenderer;
    private final Supplier<ConfirmationPrompt> confirmationFactory;
    private final CodeDefenseRuntimeProvider runtimeProvider;
    private final Supplier<GitChangePassportService> passportServiceFactory;
    private final Supplier<CodexProvenanceService> provenanceServiceFactory;

    public DefaultGitChangeDefenseRunner(GitChangeSource source, GitChangeContextBuilder contextBuilder,
            GitChangePreviewRenderer previewRenderer, Supplier<ConfirmationPrompt> confirmationFactory,
            CodeDefenseRuntimeProvider runtimeProvider, Supplier<GitChangePassportService> passportServiceFactory) {
        this(source, contextBuilder, previewRenderer, confirmationFactory, runtimeProvider,
                passportServiceFactory, () -> (repository, change, threadId) -> {
                    throw new IllegalStateException("provenance is not configured");
                });
    }

    public DefaultGitChangeDefenseRunner(GitChangeSource source, GitChangeContextBuilder contextBuilder,
            GitChangePreviewRenderer previewRenderer, Supplier<ConfirmationPrompt> confirmationFactory,
            CodeDefenseRuntimeProvider runtimeProvider, Supplier<GitChangePassportService> passportServiceFactory,
            Supplier<CodexProvenanceService> provenanceServiceFactory) {
        this.source = Objects.requireNonNull(source); this.contextBuilder = Objects.requireNonNull(contextBuilder);
        this.previewRenderer = Objects.requireNonNull(previewRenderer);
        this.confirmationFactory = Objects.requireNonNull(confirmationFactory);
        this.runtimeProvider = Objects.requireNonNull(runtimeProvider);
        this.passportServiceFactory = Objects.requireNonNull(passportServiceFactory);
        this.provenanceServiceFactory = Objects.requireNonNull(provenanceServiceFactory);
    }

    public static DefaultGitChangeDefenseRunner production(Supplier<ConfirmationPrompt> confirmationFactory) {
        GitChangeSource source = new GitCliChangeSource(new JdkProcessExecutor());
        return new DefaultGitChangeDefenseRunner(source, new GitChangeContextBuilder(),
                new GitChangePreviewRenderer(), confirmationFactory, new CodeDefenseRuntimeFactory(),
                () -> new GitChangePassportService(source,
                        new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                                new MarkdownChangePassportRenderer(), Clock.systemUTC()),
                        Clock.systemUTC(), CodexRuntimeConfig.defaults().defaultModel()),
                DefaultGitChangeDefenseRunner::productionProvenanceService);
    }

    public static DefaultGitChangeDefenseRunner productionBridge() {
        GitChangeSource source = new GitCliChangeSource(new JdkProcessExecutor());
        return new DefaultGitChangeDefenseRunner(source, new GitChangeContextBuilder(),
                new GitChangePreviewRenderer(), () -> prompt -> false, new CodeDefenseRuntimeFactory(),
                () -> new GitChangePassportService(source,
                        new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                                new MarkdownChangePassportRenderer(), Clock.systemUTC()),
                        Clock.systemUTC(), CodexRuntimeConfig.defaults().defaultModel()),
                DefaultGitChangeDefenseRunner::productionProvenanceService);
    }

    private static CodexProvenanceService productionProvenanceService() {
        var executor = new JdkProcessExecutor();
        var runtime = CodexRuntimeConfig.defaults();
        var processEnvironment = new dev.codedefense.ai.CodexProcessEnvironment();
        var preflight = dev.codedefense.ai.CodexEnvironmentChecker.forCurrentEnvironment(
                executor, runtime, processEnvironment, Path.of(".").toAbsolutePath().normalize());
        return new DefaultCodexProvenanceService(
                CodexProvenanceConfig.fromEnvironment(System.getenv()), preflight,
                new CodexChangeMatcher());
    }

    @Override public int run(Path repositoryPath, ChangeSelector selector, boolean dryRun,
            boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        return run(repositoryPath, selector, DefenseFocus.BALANCED, dryRun, skipConfirmation, out, err);
    }

    @Override public int run(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        return execute(repositoryPath, selector, focus, dryRun, CodexProvenanceRequest.disabled(),
                new TerminalChannel(skipConfirmation, out, err));
    }

    @Override public int run(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            boolean skipConfirmation, CodexProvenanceRequest provenance, PrintWriter out, PrintWriter err) {
        return execute(repositoryPath, selector, focus, dryRun, Objects.requireNonNull(provenance),
                new TerminalChannel(skipConfirmation, out, err));
    }

    /** Runs the same capture/analyze/interview/save workflow through structured bridge ports. */
    public int runBridge(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            BridgeSession bridge) {
        return runBridge(repositoryPath, selector, focus, dryRun, false, bridge);
    }

    public int runBridge(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            boolean provenanceRequested, BridgeSession bridge) {
        boolean capabilityEnabled = CodexProvenanceConfig.fromEnvironment(System.getenv()).enabled();
        return execute(repositoryPath, selector, focus, dryRun, CodexProvenanceRequest.disabled(),
                new StructuredChannel(Objects.requireNonNull(bridge, "bridge"),
                        capabilityEnabled, provenanceRequested));
    }

    private int execute(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            CodexProvenanceRequest provenanceRequest, DefenseChannel channel) {
        channel.begin();
        try {
            provenanceRequest = channel.resolveProvenance(provenanceRequest);
            CapturedGitChange captured = source.capture(repositoryPath, selector);
            Optional<CodexProvenanceSummary> provenance = provenanceRequest.enabled()
                    ? Optional.of(provenanceServiceFactory.get().capture(
                            captured.change().repositoryRoot(), captured, provenanceRequest.threadId()))
                    : Optional.empty();
            var snapshot = contextBuilder.build(captured);
            channel.preview(captured, snapshot, focus, provenance);
            if (dryRun) {
                channel.dryRunCompleted(provenance.isPresent());
                return ExitCodes.SUCCESS;
            }
            if (!channel.confirm()) {
                channel.declined();
                return ExitCodes.SUCCESS;
            }
            CodeDefenseRuntime runtime = channel.runtime(runtimeProvider);
            var analysis = new AiGitChangeAnalyzer(runtime.stagedChangeAnalyzer())
                    .analyze(captured.change(), snapshot, focus);
            Optional<EvidenceCoverageMap> coverage;
            try {
                coverage = Optional.of(new EvidenceCoverageCalculator().calculate(captured, analysis));
            } catch (RuntimeException exception) {
                coverage = Optional.empty();
            }
            channel.coveragePrepared(coverage);
            var interview = runtime.interviewRunner().conduct(analysis);
            if (interview != null) {
                SavedChangePassport saved = passportServiceFactory.get()
                        .createAndSave(selector, captured.change(), analysis, interview, focus, provenance, coverage);
                channel.saved(saved, captured, interview);
            }
            channel.completed();
            return ExitCodes.SUCCESS;
        } catch (GitChangeException exception) {
            return channel.failure("GIT_FAILURE", gitMessage(exception), gitExitCode(exception));
        } catch (EmptyProjectSnapshotException exception) {
            return channel.failure("NO_SUPPORTED_FILES", "No eligible changed source files were found.",
                    ExitCodes.NO_SUPPORTED_SOURCE_FILES);
        } catch (InvalidCodexResponseException exception) {
            return channel.failure("INVALID_MODEL_RESPONSE", "Codex returned an invalid response.",
                    ExitCodes.INVALID_MODEL_RESPONSE);
        } catch (CodexNotInstalledException exception) {
            return channel.failure("CODEX_NOT_INSTALLED", exception.getMessage(), ExitCodes.CODEX_NOT_INSTALLED);
        } catch (CodexNotAuthenticatedException exception) {
            return channel.failure("CODEX_NOT_AUTHENTICATED", exception.getMessage(),
                    ExitCodes.CODEX_NOT_AUTHENTICATED);
        } catch (CodexTimeoutException | CodexExecutionException exception) {
            return channel.failure("CODEX_EXECUTION_FAILED", exception.getMessage(),
                    ExitCodes.CODEX_EXECUTION_FAILED);
        } catch (CodexInterruptedException | InterviewCancelledException exception) {
            Thread.currentThread().interrupt();
            return channel.failure("CANCELLED", "Session cancelled. No Passport was generated.", ExitCodes.CANCELLED);
        } catch (ChangePassportPersistenceException exception) {
            return channel.failure("PASSPORT_PERSISTENCE_FAILED", exception.getMessage(),
                    ExitCodes.REPORT_PERSISTENCE_FAILED);
        }
    }

    private static int gitExitCode(GitChangeException exception) {
        if (exception.kind() == GitChangeException.Kind.INVALID_REPOSITORY) return ExitCodes.INVALID_PROJECT_PATH;
        if (exception.kind() == GitChangeException.Kind.NO_STAGED_CHANGE) return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        return ExitCodes.GIT_EXECUTION_FAILED;
    }

    private static String gitMessage(GitChangeException exception) {
        if (exception.kind() == GitChangeException.Kind.INVALID_REPOSITORY) {
            return "The supplied path is not a readable Git repository.";
        }
        if (exception.kind() == GitChangeException.Kind.NO_STAGED_CHANGE) {
            return "No eligible staged source files were found.";
        }
        return "Git could not safely capture the selected change.";
    }

    private static String displayName(dev.codedefense.domain.ChangeKind kind) {
        return switch (kind) {
            case STAGED -> "Staged change";
            case COMMIT -> "Commit";
            case RANGE -> "Range";
        };
    }

    private interface DefenseChannel {
        void begin();
        CodexProvenanceRequest resolveProvenance(CodexProvenanceRequest request);
        void preview(CapturedGitChange captured, ProjectSnapshot snapshot, DefenseFocus focus,
                Optional<CodexProvenanceSummary> provenance);
        boolean confirm();
        void dryRunCompleted(boolean provenanceRequested);
        void declined();
        CodeDefenseRuntime runtime(CodeDefenseRuntimeProvider provider);
        void coveragePrepared(Optional<EvidenceCoverageMap> coverage);
        void saved(SavedChangePassport saved, CapturedGitChange captured, InterviewSession interview);
        void completed();
        int failure(String code, String message, int exitCode);
    }

    private final class TerminalChannel implements DefenseChannel {
        private final boolean skipConfirmation;
        private final PrintWriter out;
        private final PrintWriter err;

        private TerminalChannel(boolean skipConfirmation, PrintWriter out, PrintWriter err) {
            this.skipConfirmation = skipConfirmation;
            this.out = Objects.requireNonNull(out, "out");
            this.err = Objects.requireNonNull(err, "err");
        }

        @Override public void begin() { }

        @Override public CodexProvenanceRequest resolveProvenance(CodexProvenanceRequest request) {
            return request;
        }

        @Override public void preview(CapturedGitChange captured, ProjectSnapshot snapshot, DefenseFocus focus,
                Optional<CodexProvenanceSummary> provenance) {
            previewRenderer.render(captured.change(), snapshot, out);
            out.println("Focus: " + focus.displayName());
            provenance.ifPresent(value -> {
                out.println("Experimental Codex provenance: " + provenanceDisplay(value));
                out.println("Evidence consistency only; this does not prove authorship, causation, review quality, or safety.");
            });
        }

        @Override public boolean confirm() {
            if (skipConfirmation) return true;
            String prompt = "Send bounded Git change context to Codex? [y/N]";
            out.println(prompt);
            return confirmationFactory.get().confirm(prompt);
        }

        @Override public void dryRunCompleted(boolean provenanceRequested) {
            out.println("No source content was sent.");
            out.println("No model request was made.");
            if (provenanceRequested) {
                out.println("Codex app-server may have been invoked only to read the selected local thread.");
            } else {
                out.println("Codex was not invoked.");
            }
        }

        @Override public void declined() {
            out.println("Cancelled before any source content was sent.");
        }

        @Override public CodeDefenseRuntime runtime(CodeDefenseRuntimeProvider provider) {
            return provider.create(out);
        }

        @Override public void coveragePrepared(Optional<EvidenceCoverageMap> coverage) { }

        @Override public void saved(SavedChangePassport saved, CapturedGitChange captured,
                InterviewSession interview) {
            out.println("Change Passport saved: " + saved.path().getFileName());
            out.println("Status: " + saved.status());
            out.println("Overall score: " + interview.overallScore() + "/100");
            out.println("Fingerprint: " + captured.change().diffFingerprint().substring(0, 12));
            out.println("Next:");
            out.println("  codedefense passport show .");
            out.println("  codedefense passport verify .");
            out.println("  codedefense passport export . --format json --output passport.json");
        }

        @Override public void completed() { }

        @Override public int failure(String code, String message, int exitCode) {
            err.println(message == null || message.isBlank() ? "CodeDefense workflow failed." : message);
            return exitCode;
        }
    }

    private static final class StructuredChannel implements DefenseChannel {
        private final BridgeSession bridge;
        private final boolean capabilityEnabled;
        private final boolean provenanceRequested;
        private final dev.codedefense.bridge.BridgeEvidenceCoveragePublisher coveragePublisher;

        private StructuredChannel(BridgeSession bridge, boolean capabilityEnabled, boolean provenanceRequested) {
            this.bridge = bridge;
            this.capabilityEnabled = capabilityEnabled;
            this.provenanceRequested = provenanceRequested;
            this.coveragePublisher = new dev.codedefense.bridge.BridgeEvidenceCoveragePublisher(bridge);
        }

        @Override public void begin() {
            java.util.ArrayList<String> capabilities = new java.util.ArrayList<>(
                    java.util.List.of("interactiveDefenseV1", "passportStatusV1"));
            if (capabilityEnabled) capabilities.add("codexProvenanceV1");
            if (bridge.protocolVersion() >= BridgeProtocol.VERSION_3) capabilities.add("evidenceCoverageV1");
            bridge.emit(new BridgeEvent.HelloEvent(BridgeProtocol.VERSION, capabilities));
        }

        @Override public CodexProvenanceRequest resolveProvenance(CodexProvenanceRequest request) {
            if (!provenanceRequested) return request;
            if (!capabilityEnabled) throw new dev.codedefense.bridge.BridgeProtocolException(
                    "Experimental Codex provenance is disabled.");
            var bridgeRequest = bridge.readRequest().orElseThrow(() ->
                    new dev.codedefense.bridge.BridgeProtocolException("Provenance consent was not provided."));
            if (!(bridgeRequest instanceof dev.codedefense.bridge.ProvenanceConsentRequest consent)) {
                throw new dev.codedefense.bridge.BridgeProtocolException("Provenance consent was not provided.");
            }
            return CodexProvenanceRequest.enabled(consent.threadId());
        }

        @Override public void preview(CapturedGitChange captured, ProjectSnapshot snapshot, DefenseFocus focus,
                Optional<CodexProvenanceSummary> provenance) {
            bridge.emit(new BridgeEvent.PreviewEvent(BridgeProtocol.VERSION, snapshot.projectName(),
                    displayName(captured.change().kind()), focus.cliName(), snapshot.selectedFiles().size(),
                    captured.change().addedLines(), captured.change().deletedLines()));
            provenance.ifPresent(value -> bridge.emit(new BridgeEvent.ProvenanceEvent(
                    BridgeProtocol.VERSION, provenanceDisplay(value),
                    "Evidence consistency only; this does not prove authorship, causation, review quality, or safety.")));
        }

        @Override public boolean confirm() {
            return new BridgeConfirmationPrompt(bridge).confirm(
                    "Send bounded Git change context to the locally authenticated Codex CLI?");
        }

        @Override public void dryRunCompleted(boolean provenanceRequested) {
            bridge.emit(new BridgeEvent.CompletedEvent(BridgeProtocol.VERSION, ExitCodes.SUCCESS, false));
        }

        @Override public void declined() {
            bridge.emit(new BridgeEvent.CompletedEvent(BridgeProtocol.VERSION, ExitCodes.SUCCESS, false));
        }

        @Override public CodeDefenseRuntime runtime(CodeDefenseRuntimeProvider provider) {
            return provider.create(new BridgeInterviewInput(bridge),
                    new BridgeInterviewOutput(bridge, coveragePublisher));
        }

        @Override public void coveragePrepared(Optional<EvidenceCoverageMap> coverage) {
            coverage.ifPresent(coveragePublisher::prepare);
        }

        @Override public void saved(SavedChangePassport saved, CapturedGitChange captured,
                InterviewSession interview) {
            bridge.emit(new BridgeEvent.PassportSavedEvent(BridgeProtocol.VERSION, saved.path().toString(),
                    saved.status().name(), captured.change().diffFingerprint().substring(0, 12)));
        }

        @Override public void completed() {
            bridge.emit(new BridgeEvent.CompletedEvent(BridgeProtocol.VERSION, ExitCodes.SUCCESS, true));
        }

        @Override public int failure(String code, String message, int exitCode) {
            String safe = message == null || message.isBlank() ? "CodeDefense workflow failed." : message;
            bridge.emit(new BridgeEvent.ErrorEvent(BridgeProtocol.VERSION, code, safe, exitCode));
            return exitCode;
        }
    }

    private static String provenanceDisplay(CodexProvenanceSummary value) {
        return switch (value.status()) {
            case EXACT_CHANGE_MATCH -> "Exact change match";
            case PARTIAL_PATH_MATCH -> "Partial path match";
            case NO_MATCH -> "No match";
            case UNAVAILABLE -> "Unavailable";
        };
    }
}
