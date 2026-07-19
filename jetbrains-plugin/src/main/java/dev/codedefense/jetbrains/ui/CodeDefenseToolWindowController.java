package dev.codedefense.jetbrains.ui;

import com.intellij.openapi.Disposable;
import dev.codedefense.jetbrains.process.BridgeLineCodec;
import dev.codedefense.jetbrains.process.BridgeLineCodec.BridgeMessage;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.BridgeLaunchSpec;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.Selector;
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
        void send(byte[] request);
        void cancel();
        CompletableFuture<Integer> completion();
    }

    interface StatusLoader { dev.codedefense.jetbrains.status.PassportStatusView load(); }
    interface PassportOpener { void open(Path path); }

    private final CodeDefenseToolWindowView view;
    private final SessionLauncher launcher;
    private final BridgeLineCodec codec;
    private final Consumer<Runnable> edt;
    private final Executor background;
    private final StatusLoader statusLoader;
    private final PassportOpener passportOpener;
    private final ScheduledExecutorService debounce = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("codedefense-status-refresh").factory());
    private final AtomicLong refreshGeneration = new AtomicLong();
    private final Object lock = new Object();
    private volatile Session activeSession;
    private boolean starting;
    private volatile Path passportPath;
    private byte[] pendingProvenanceRequest;
    private boolean provenanceCapabilitySeen;
    private boolean confirmationPending;

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt) {
        this(view, launcher, codec, edt, Runnable::run, null, null);
    }

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt, Executor background) {
        this(view, launcher, codec, edt, background, null, null);
    }

    CodeDefenseToolWindowController(CodeDefenseToolWindowView view, SessionLauncher launcher,
            BridgeLineCodec codec, Consumer<Runnable> edt, Executor background,
            StatusLoader statusLoader, PassportOpener passportOpener) {
        this.view = Objects.requireNonNull(view, "view");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.edt = Objects.requireNonNull(edt, "edt");
        this.background = Objects.requireNonNull(background, "background");
        this.statusLoader = statusLoader;
        this.passportOpener = passportOpener;
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

    private void beginProvenance(BridgeLaunchSpec spec, String threadId, boolean consent) {
        byte[] request = codec.provenanceConsentRequest(threadId, consent);
        synchronized (lock) {
            if (starting || activeSession != null) {
                java.util.Arrays.fill(request, (byte) 0);
                throw new IllegalStateException("A CodeDefense session is already active.");
            }
            pendingProvenanceRequest = request;
            provenanceCapabilitySeen = false;
        }
        try { begin(spec); }
        catch (RuntimeException exception) { clearPendingProvenance(); throw exception; }
    }

    private void begin(BridgeLaunchSpec spec) {
        synchronized (lock) {
            if (starting || activeSession != null) {
                throw new IllegalStateException("A CodeDefense session is already active.");
            }
            starting = true;
        }
        ui(() -> view.setSessionActive(true));
        background.execute(() -> {
            try {
                Session launched = launcher.launch(spec, this::onEvent);
                synchronized (lock) {
                    activeSession = launched;
                    starting = false;
                }
                sendProvenanceIfReady();
                launched.completion().whenComplete((exit, failure) -> finish(failure));
            } catch (RuntimeException exception) {
                synchronized (lock) { starting = false; }
                ui(() -> {
                    view.showError("CodeDefense could not be started.");
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
        current.send(codec.confirmRequest(accepted));
    }

    public void answer(String answer) {
        byte[] request = codec.answerRequest(answer);
        session().send(request);
        ui(view::clearAnswer);
    }

    public void skip() {
        session().send(codec.skipRequest());
        ui(view::clearAnswer);
    }

    public void cancel() {
        Session session;
        synchronized (lock) {
            session = activeSession;
            activeSession = null;
            starting = false;
            confirmationPending = false;
        }
        clearPendingProvenance();
        if (session != null) {
            session.cancel();
        }
        ui(() -> view.setSessionActive(false));
    }

    private Session session() {
        Session current = activeSession;
        if (current == null) {
            throw new IllegalStateException("No CodeDefense session is active.");
        }
        return current;
    }

    private void onEvent(BridgeMessage event) {
        if (event.type().equals("hello")) {
            boolean requested;
            synchronized (lock) {
                provenanceCapabilitySeen = event.strings("capabilities").contains("codexProvenanceV1");
                requested = pendingProvenanceRequest != null;
            }
            if (requested && !provenanceCapabilitySeen) {
                ui(() -> view.showError("Experimental Codex provenance is unavailable in this core."));
                cancel();
                return;
            }
            sendProvenanceIfReady();
        }
        if (event.type().equals("confirmationRequired")) {
            synchronized (lock) { confirmationPending = true; }
        }
        ui(() -> {
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
                case "question" -> view.showQuestion((event.bool("followUp") ? "Follow-up" : "Question "
                        + event.integer("number") + "/" + event.integer("total")) + ": "
                        + safe(event.text("prompt")));
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
                case "completed" -> view.showCompleted("Completed with exit code "
                        + event.integer("exitCode") + ". Codex invoked: " + event.bool("codexInvoked"));
                case "error" -> view.showError(safe(event.text("message")));
                default -> view.showError("CodeDefense returned an unsupported bridge event.");
            }
        });
    }

    private void sendProvenanceIfReady() {
        Session session;
        byte[] request;
        synchronized (lock) {
            if (!provenanceCapabilitySeen || activeSession == null || pendingProvenanceRequest == null) return;
            session = activeSession;
            request = pendingProvenanceRequest;
            pendingProvenanceRequest = null;
        }
        try {
            session.send(request);
            ui(view::clearProvenanceConsent);
        } finally {
            java.util.Arrays.fill(request, (byte) 0);
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

    private void finish(Throwable failure) {
        synchronized (lock) {
            activeSession = null;
            starting = false;
            confirmationPending = false;
        }
        ui(() -> {
            if (failure != null) {
                view.showError("CodeDefense bridge execution failed.");
            }
            view.setSessionActive(false);
        });
    }

    public void refresh() {
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
        long generation = refreshGeneration.incrementAndGet();
        debounce.schedule(() -> {
            if (refreshGeneration.get() == generation) refresh();
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
        cancel();
        debounce.shutdownNow();
    }

    private void clearPendingProvenance() {
        synchronized (lock) {
            if (pendingProvenanceRequest != null) java.util.Arrays.fill(pendingProvenanceRequest, (byte) 0);
            pendingProvenanceRequest = null;
            provenanceCapabilitySeen = false;
        }
    }
}
