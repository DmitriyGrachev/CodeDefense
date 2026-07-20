package dev.codedefense.jetbrains.ui;

import com.intellij.openapi.Disposable;
import dev.codedefense.jetbrains.process.BridgeLineCodec;
import dev.codedefense.jetbrains.process.BridgeLineCodec.BridgeMessage;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.BridgeLaunchSpec;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.Selector;
import dev.codedefense.jetbrains.evidence.EvidenceNavigator;
import dev.codedefense.jetbrains.insights.RepositoryInsightsView;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class CodeDefenseToolWindowController implements Disposable {
    interface SessionLauncher {
        Session launch(BridgeLaunchSpec spec, Consumer<BridgeMessage> eventConsumer);
    }

    interface Session {
        void confirm(boolean accepted);
        void answer(String answer);
        void skip();
        void provenanceConsent(String threadId, boolean consent);
        void cancel();
        CompletableFuture<Integer> completion();
    }

    interface StatusLoader { dev.codedefense.jetbrains.status.PassportStatusView load(); }
    interface PassportOpener { void open(Path path); }
    interface InsightsLoader { RepositoryInsightsView load(); }

    private final CodeDefenseToolWindowView view;
    private final SessionLauncher launcher;
    private final BridgeLineCodec codec;
    private final Consumer<Runnable> edt;
    private final Executor background;
    private final StatusLoader statusLoader;
    private final PassportOpener passportOpener;
    private final Runnable gateRefresh;
    private final EvidenceNavigator evidenceNavigator;
    private final InsightsLoader insightsLoader;
    private final ScheduledExecutorService debounce = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("codedefense-status-refresh").factory());
    private final AtomicLong refreshGeneration = new AtomicLong();
    private final AtomicLong insightsGeneration = new AtomicLong();
    private final Object lock = new Object();
    private long sessionGeneration;
    private volatile Session activeSession;
    private boolean starting;
    private volatile Path passportPath;
    private PendingProvenance pendingProvenance;
    private boolean provenanceCapabilitySeen;
    private boolean confirmationPending;

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt) {
        this(view, launcher, codec, edt, Runnable::run, null, null, () -> { });
    }

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt, Executor background) {
        this(view, launcher, codec, edt, background, null, null, () -> { });
    }

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt, Executor background,
            StatusLoader statusLoader, PassportOpener passportOpener) {
        this(view, launcher, codec, edt, background, statusLoader, passportOpener, () -> { });
    }

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt, Executor background,
            StatusLoader statusLoader, PassportOpener passportOpener, Runnable gateRefresh) {
        this(view, launcher, codec, edt, background, statusLoader, passportOpener, gateRefresh,
                location -> new EvidenceNavigator.NavigationResult(
                        EvidenceNavigator.NavigationStatus.UNAVAILABLE,
                        "Evidence navigation is unavailable."));
    }

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt, Executor background,
            StatusLoader statusLoader, PassportOpener passportOpener, Runnable gateRefresh,
            EvidenceNavigator evidenceNavigator) {
        this(view, launcher, codec, edt, background, statusLoader, passportOpener, gateRefresh,
                evidenceNavigator, null);
    }

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt, Executor background,
            StatusLoader statusLoader, PassportOpener passportOpener, Runnable gateRefresh,
            EvidenceNavigator evidenceNavigator, InsightsLoader insightsLoader) {
        this.view = Objects.requireNonNull(view, "view");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.background = Objects.requireNonNull(background, "background");
        this.statusLoader = statusLoader;
        this.passportOpener = passportOpener;
        this.gateRefresh = Objects.requireNonNull(gateRefresh, "gateRefresh");
        this.evidenceNavigator = Objects.requireNonNull(evidenceNavigator, "evidenceNavigator");
        this.insightsLoader = insightsLoader;
    }

    public void preview(Selector selector, String selectorValue, String focus) {
        begin(new BridgeLaunchSpec(selector, selectorValue, focus, true));
    }

    public void preview(Selector selector, String selectorValue, String focus,
            String threadId, boolean consent) {
        beginProvenance(new BridgeLaunchSpec(selector, selectorValue, focus, true, true), threadId, consent);
    }

    public void start(Selector selector, String selectorValue, String focus) {
        begin(new BridgeLaunchSpec(selector, selectorValue, focus, false));
    }

    public void start(Selector selector, String selectorValue, String focus,
            String threadId, boolean consent) {
        beginProvenance(new BridgeLaunchSpec(selector, selectorValue, focus, false, true), threadId, consent);
    }

    public void defendStagedChange() {
        ui(view::prepareStagedDefense);
    }

    private void beginProvenance(BridgeLaunchSpec spec, String threadId, boolean consent) {
        byte[] validation = codec.provenanceConsentRequest(threadId, consent);
        java.util.Arrays.fill(validation, (byte) 0);
        synchronized (lock) {
            if (starting || activeSession != null) {
                throw new IllegalStateException("A CodeDefense session is already active.");
            }
            pendingProvenance = new PendingProvenance(threadId, consent);
            provenanceCapabilitySeen = false;
        }
        try { begin(spec); }
        catch (RuntimeException exception) { clearPendingProvenance(); throw exception; }
    }

    private void begin(BridgeLaunchSpec spec) {
        long generation;
        synchronized (lock) {
            if (starting || activeSession != null) {
                throw new IllegalStateException("A CodeDefense session is already active.");
            }
            generation = ++sessionGeneration;
            starting = true;
        }
        uiCurrent(generation, () -> {
            view.clearEvidence();
            view.setSessionActive(true);
        });
        background.execute(() -> {
            try {
                Session launched = launcher.launch(spec, event -> onEvent(generation, event));
                boolean stale;
                synchronized (lock) {
                    stale = generation != sessionGeneration || !starting;
                    if (!stale) {
                        activeSession = launched;
                        starting = false;
                    }
                }
                if (stale) {
                    launched.cancel();
                    return;
                }
                sendProvenanceIfReady(generation);
                launched.completion().whenComplete((exit, failure) -> finish(generation, failure));
            } catch (RuntimeException exception) {
                synchronized (lock) {
                    if (generation != sessionGeneration) return;
                    starting = false;
                    clearPendingProvenanceLocked();
                }
                uiCurrent(generation, () -> {
                    view.showError("CodeDefense could not be started.");
                    view.clearEvidence();
                    view.setSessionActive(false);
                });
            }
        });
    }

    public void confirm(boolean accepted) {
        Session current;
        synchronized (lock) {
            if (!confirmationPending) return;
            confirmationPending = false;
            current = activeSession;
        }
        if (current == null) return;
        ui(() -> view.setConfirmationEnabled(false));
        current.confirm(accepted);
    }

    public void answer(String answer) {
        Session current = session();
        current.answer(answer);
        ui(view::clearAnswer);
    }

    public void skip() {
        Session current = session();
        current.skip();
        ui(view::clearAnswer);
    }

    public void cancel() {
        Session session;
        long generation;
        synchronized (lock) {
            generation = ++sessionGeneration;
            session = activeSession;
            activeSession = null;
            starting = false;
            confirmationPending = false;
            clearPendingProvenanceLocked();
        }
        if (session != null) {
            session.cancel();
        }
        uiCurrent(generation, () -> {
            view.clearEvidence();
            view.setSessionActive(false);
        });
    }

    private Session session() {
        Session current = activeSession;
        if (current == null) {
            throw new IllegalStateException("No CodeDefense session is active.");
        }
        return current;
    }

    private void onEvent(long generation, BridgeMessage event) {
        synchronized (lock) {
            if (generation != sessionGeneration || (!starting && activeSession == null)) return;
        }
        if (event.type().equals("hello")) {
            boolean requested;
            synchronized (lock) {
                if (generation != sessionGeneration) return;
                provenanceCapabilitySeen = event.strings("capabilities").contains("codexProvenanceV1");
                requested = pendingProvenance != null;
            }
            if (requested && !provenanceCapabilitySeen) {
                ui(() -> view.showError("Experimental Codex provenance is unavailable in this core."));
                cancel();
                return;
            }
            sendProvenanceIfReady(generation);
        }
        if (event.type().equals("confirmationRequired")) {
            synchronized (lock) {
                if (generation != sessionGeneration) return;
                confirmationPending = true;
            }
        }
        uiCurrent(generation, () -> {
            switch (event.type()) {
                case "hello" -> { }
                case "preview" -> view.showPreview(safe(event.text("projectName")) + " | "
                        + safe(event.text("changeKind")) + " | " + safe(event.text("focus")) + " | "
                        + event.integer("selectedFiles") + " files | +" + event.integer("addedLines")
                        + "/-" + event.integer("deletedLines"));
                case "confirmationRequired" -> {
                    view.setConfirmationEnabled(true);
                    view.showConfirmation(safe(event.text("message")));
                }
                case "question" -> {
                    boolean followUp = event.bool("followUp");
                    view.showQuestion((followUp ? "Follow-up" : "Question "
                            + event.integer("number") + "/" + event.integer("total")) + ": "
                            + safe(event.text("prompt")));
                    if (!followUp) {
                        view.showEvidence(event.evidence(), evidenceNavigator::open);
                    }
                }
                case "evaluation" -> view.showEvaluation(safe(event.text("verdict")) + " "
                        + event.integer("score") + "/100\n" + safe(event.text("feedback")));
                case "questionScore" -> view.showQuestionScore("Question " + event.integer("questionNumber")
                        + " score: " + event.integer("score") + "/100");
                case "summary" -> view.showSummary(summary(event.integers("questionScores"),
                        event.integer("overallScore"), safe(event.text("readiness"))));
                case "passportSaved" -> {
                    passportPath = Path.of(event.text("path")).toAbsolutePath().normalize();
                    view.showPassportSaved(passportPath.toString(), "Passport "
                            + safe(event.text("status")) + " | " + safe(event.text("shortFingerprint")));
                    scheduleRefresh();
                }
                case "provenance" -> view.showProvenance(safe(event.text("status")) + "\n"
                        + safe(event.text("disclaimer")));
                case "completed" -> {
                    view.showCompleted("Completed with exit code "
                            + event.integer("exitCode") + ". Codex invoked: " + event.bool("codexInvoked"));
                    view.clearEvidence();
                }
                case "error" -> {
                    view.showError(safe(event.text("message")));
                    if (!"INVALID_ANSWER".equals(event.text("code"))) {
                        view.clearEvidence();
                    }
                }
                default -> {
                    view.showError("CodeDefense returned an unsupported bridge event.");
                    view.clearEvidence();
                }
            }
        });
    }

    private void sendProvenanceIfReady(long generation) {
        Session session;
        PendingProvenance pending;
        synchronized (lock) {
            if (generation != sessionGeneration || !provenanceCapabilitySeen
                    || activeSession == null || pendingProvenance == null) return;
            session = activeSession;
            pending = pendingProvenance;
            pendingProvenance = null;
        }
        try {
            session.provenanceConsent(pending.threadId(), pending.consent());
            ui(view::clearProvenanceConsent);
        } finally {
            pending.clear();
        }
    }

    private String summary(List<Integer> scores, int overallScore, String readiness) {
        StringBuilder value = new StringBuilder("Question scores");
        for (int index = 0; index < scores.size(); index++) {
            value.append('\n').append(index + 1).append(". ").append(scores.get(index)).append("/100");
        }
        return value.append("\nOverall score: ").append(overallScore).append("/100\nReadiness: ")
                .append(readiness).toString();
    }

    private void finish(long generation, Throwable failure) {
        synchronized (lock) {
            if (generation != sessionGeneration) return;
            activeSession = null;
            starting = false;
            confirmationPending = false;
            clearPendingProvenanceLocked();
        }
        uiCurrent(generation, () -> {
            if (failure != null) {
                view.showError("CodeDefense bridge execution failed.");
            }
            view.clearEvidence();
            view.setSessionActive(false);
        });
    }

    public void refresh() {
        gateRefresh.run();
        refreshPassportStatus();
        refreshInsights();
    }

    void initialize(boolean toolWindowVisible) {
        gateRefresh.run();
        refreshPassportStatus();
        if (toolWindowVisible) refreshInsights();
    }

    public void refreshInsights() {
        if (insightsLoader == null) return;
        long generation = insightsGeneration.incrementAndGet();
        background.execute(() -> {
            try {
                RepositoryInsightsView insights = insightsLoader.load();
                uiInsightsCurrent(generation, () -> view.showRepositoryInsights(insights));
            } catch (RuntimeException exception) {
                uiInsightsCurrent(generation, view::showRepositoryInsightsUnavailable);
            }
        });
    }

    private void refreshPassportStatus() {
        if (statusLoader == null) return;
        background.execute(() -> {
            try {
                var status = statusLoader.load();
                ui(() -> view.showPassportStatus(statusText(status)));
            } catch (RuntimeException exception) {
                ui(() -> view.showPassportStatus("Passport status unavailable"));
            }
        });
    }

    public void scheduleRefresh() {
        gateRefresh.run();
        refreshInsights();
        long generation = refreshGeneration.incrementAndGet();
        debounce.schedule(() -> {
            if (refreshGeneration.get() == generation) refreshPassportStatus();
        }, 500, TimeUnit.MILLISECONDS);
    }

    public void openPassport() {
        Path current = passportPath;
        if (current == null || passportOpener == null) {
            ui(() -> view.showError("No saved Passport is available to open."));
            return;
        }
        background.execute(() -> {
            try { passportOpener.open(current); }
            catch (RuntimeException exception) { ui(() -> view.showError("The saved Passport could not be opened.")); }
        });
    }

    private String statusText(dev.codedefense.jetbrains.status.PassportStatusView status) {
        if (!status.present()) return "No Passport";
        StringBuilder value = new StringBuilder(status.status()).append(" | ")
                .append(status.changeKind()).append(" | ").append(status.shortFingerprint())
                .append(" | ").append(status.focus()).append(" | attempt ").append(status.attemptNumber());
        status.categories().forEach(category -> value.append("\n").append(safe(category.id()))
                .append(": ").append(category.score()).append("/100"));
        return value.append("\nOverall: ").append(status.overallScore()).append("/100 | ")
                .append(safe(status.readiness())).toString();
    }

    private void ui(Runnable action) {
        edt.accept(action);
    }

    private void uiCurrent(long generation, Runnable action) {
        ui(() -> {
            synchronized (lock) {
                if (generation != sessionGeneration) return;
            }
            action.run();
        });
    }

    private void uiInsightsCurrent(long generation, Runnable action) {
        ui(() -> {
            if (insightsGeneration.get() != generation) return;
            action.run();
        });
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(Math.min(value.length(), 16_384));
        boolean escape = false;
        for (int index = 0; index < value.length() && safe.length() < 16_384; index++) {
            char current = value.charAt(index);
            if (escape) {
                if (current >= '@' && current <= '~') escape = false;
                continue;
            }
            if (current == 0x1B) { escape = true; continue; }
            if (current == '\n' || current == '\t' || current >= 0x20) safe.append(current);
        }
        return safe.toString();
    }

    @Override
    public void dispose() {
        insightsGeneration.incrementAndGet();
        cancel();
        debounce.shutdownNow();
    }

    private void clearPendingProvenance() {
        synchronized (lock) {
            clearPendingProvenanceLocked();
        }
    }

    private void clearPendingProvenanceLocked() {
        if (pendingProvenance != null) pendingProvenance.clear();
        pendingProvenance = null;
        provenanceCapabilitySeen = false;
    }

    private static final class PendingProvenance {
        private String threadId;
        private final boolean consent;

        private PendingProvenance(String threadId, boolean consent) {
            this.threadId = threadId;
            this.consent = consent;
        }

        private String threadId() { return threadId; }
        private boolean consent() { return consent; }
        private void clear() { threadId = null; }

        @Override public String toString() {
            return "PendingProvenance[threadIdLength=" + (threadId == null ? 0 : threadId.length()) + "]";
        }
    }
}
