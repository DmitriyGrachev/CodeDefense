package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.jetbrains.process.BridgeLineCodec;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.BridgeLaunchSpec;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.Selector;
import dev.codedefense.jetbrains.process.EvidenceLocationView;
import dev.codedefense.jetbrains.evidence.EvidenceNavigator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CodeDefenseToolWindowControllerTest {
    @Test
    void primaryQuestionReplacesEvidenceWhileFollowUpRetainsItAndClickNavigatesOnce() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        List<EvidenceLocationView> opened = new ArrayList<>();
        EvidenceNavigator navigator = location -> {
            opened.add(location);
            return new EvidenceNavigator.NavigationResult(
                    EvidenceNavigator.NavigationStatus.OPENED, "Evidence opened.");
        };
        var controller = new CodeDefenseToolWindowController(view, launcher, new BridgeLineCodec(),
                Runnable::run, Runnable::run, null, null, () -> { }, navigator);
        controller.start(Selector.STAGED, null, "balanced");

        launcher.event(question(false, "src/First.java", 4, 9));
        assertEquals(List.of(new EvidenceLocationView("src/First.java", 4, 9)), view.evidence);
        view.openEvidence(0);
        assertEquals(List.of(new EvidenceLocationView("src/First.java", 4, 9)), opened);

        launcher.event("{\"protocolVersion\":2,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":true,\"prompt\":\"Clarify\",\"evidence\":[]}\n");
        assertEquals(List.of(new EvidenceLocationView("src/First.java", 4, 9)), view.evidence);

        launcher.event(question(false, "src/Second.java", 2, 2));
        assertEquals(List.of(new EvidenceLocationView("src/Second.java", 2, 2)), view.evidence);
    }

    @Test
    void evidenceIsClearedForNewSessionCompletionAndError() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        EvidenceNavigator navigator = location -> new EvidenceNavigator.NavigationResult(
                EvidenceNavigator.NavigationStatus.OPENED, "Evidence opened.");
        var controller = new CodeDefenseToolWindowController(view, launcher, new BridgeLineCodec(),
                Runnable::run, Runnable::run, null, null, () -> { }, navigator);

        controller.start(Selector.STAGED, null, "balanced");
        launcher.event(question(false, "src/Main.java", 1, 1));
        launcher.event("{\"protocolVersion\":2,\"type\":\"completed\",\"exitCode\":0,"
                + "\"codexInvoked\":true}\n");
        assertTrue(view.evidence.isEmpty());

        launcher.session.completion.complete(0);
        FakeLauncher second = new FakeLauncher();
        controller = new CodeDefenseToolWindowController(view, second, new BridgeLineCodec(),
                Runnable::run, Runnable::run, null, null, () -> { }, navigator);
        view.evidence = List.of(new EvidenceLocationView("stale.java", 1, 1));
        controller.start(Selector.STAGED, null, "balanced");
        assertTrue(view.evidence.isEmpty());
        second.event(question(false, "src/Other.java", 1, 1));
        second.event("{\"protocolVersion\":2,\"type\":\"error\",\"code\":\"SAFE\","
                + "\"message\":\"Safe failure\",\"exitCode\":8}\n");
        assertTrue(view.evidence.isEmpty());
    }

    @Test
    void recoverableInvalidAnswerErrorRetainsEvidenceForTheRepromptedQuestion() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        EvidenceNavigator navigator = location -> new EvidenceNavigator.NavigationResult(
                EvidenceNavigator.NavigationStatus.OPENED, "Evidence opened.");
        var controller = new CodeDefenseToolWindowController(view, launcher, new BridgeLineCodec(),
                Runnable::run, Runnable::run, null, null, () -> { }, navigator);
        controller.start(Selector.STAGED, null, "balanced");
        var expected = new EvidenceLocationView("src/Main.java", 7, 11);
        launcher.event(question(false, expected.relativePath(), expected.startLine(), expected.endLine()));

        launcher.event("{\"protocolVersion\":2,\"type\":\"error\",\"code\":\"INVALID_ANSWER\","
                + "\"message\":\"Answer is invalid. Try again.\",\"exitCode\":2}\n");

        assertEquals(List.of(expected), view.evidence);
        assertTrue(view.text.contains("Answer is invalid. Try again."));
    }

    @Test
    void previewStartsDryRunAndRendersTrustedMetadataWithoutConfirmation() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        var controller = controller(view, launcher);

        controller.preview(Selector.STAGED, null, "testing");
        launcher.event("{\"protocolVersion\":1,\"type\":\"preview\",\"projectName\":\"demo\","
                + "\"changeKind\":\"STAGED\",\"focus\":\"testing\",\"selectedFiles\":2,"
                + "\"addedLines\":4,\"deletedLines\":1}\n");
        launcher.event("{\"protocolVersion\":1,\"type\":\"completed\",\"exitCode\":0,"
                + "\"codexInvoked\":false}\n");
        launcher.session.completion.complete(0);

        assertTrue(launcher.spec.dryRun());
        assertTrue(view.text.contains("demo | STAGED | testing | 2 files | +4/-1"));
        assertFalse(view.confirmationShown);
        assertFalse(view.active);
    }

    @Test
    void acceptedRunHandlesQuestionSkipEvaluationPassportAndCompletion() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        var controller = controller(view, launcher);
        controller.start(Selector.COMMIT, "HEAD~1", "balanced");

        launcher.event("{\"protocolVersion\":1,\"type\":\"confirmationRequired\","
                + "\"message\":\"Bounded source will be sent.\"}\n");
        controller.confirm(true);
        launcher.event("{\"protocolVersion\":1,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":false,\"prompt\":\"Explain\\u001b[31m this\"}\n");
        controller.answer("PRIVATE-ANSWER");
        controller.skip();
        launcher.event("{\"protocolVersion\":1,\"type\":\"evaluation\",\"verdict\":\"PARTIAL\","
                + "\"score\":61,\"feedback\":\"Useful\",\"understoodConcepts\":[],"
                + "\"missingConcepts\":[]}\n");
        launcher.event("{\"protocolVersion\":1,\"type\":\"passportSaved\","
                + "\"path\":\"C:/safe/passport.md\",\"status\":\"CURRENT\","
                + "\"shortFingerprint\":\"abc123\"}\n");
        launcher.event("{\"protocolVersion\":1,\"type\":\"completed\",\"exitCode\":0,"
                + "\"codexInvoked\":true}\n");
        launcher.session.completion.complete(0);

        assertTrue(launcher.requests.stream().anyMatch(line -> line.contains("\"accepted\":true")));
        assertTrue(launcher.requests.stream().anyMatch(line -> line.contains("PRIVATE-ANSWER")));
        assertTrue(launcher.requests.stream().anyMatch(line -> line.contains("\"type\":\"skip\"")));
        assertTrue(view.answerCleared);
        assertTrue(view.text.stream().noneMatch(value -> value.contains("\u001b")));
        assertTrue(view.text.stream().anyMatch(value -> value.contains("Question 1/3")));
        assertTrue(view.text.stream().anyMatch(value -> value.contains("PARTIAL 61/100")));
        assertEquals(java.nio.file.Path.of("C:/safe/passport.md").toAbsolutePath().normalize().toString(),
                view.passportPath);
        assertFalse(view.active);
    }

    @Test
    void confirmationCanBeSentOnlyOncePerPrompt() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        var controller = controller(view, launcher);
        controller.start(Selector.STAGED, null, "balanced");

        launcher.event("{\"protocolVersion\":1,\"type\":\"confirmationRequired\","
                + "\"message\":\"Bounded source will be sent.\"}\n");
        controller.confirm(true);
        controller.confirm(true);

        assertEquals(1, launcher.requests.stream()
                .filter(line -> line.contains("\"type\":\"confirm\""))
                .count());
        assertFalse(view.confirmationEnabled);
    }

    @Test
    void declineErrorCancelDoubleStartAndDisposeAreSafe() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        var controller = controller(view, launcher);
        controller.start(Selector.RANGE, "main..feature", "architecture");

        assertThrows(IllegalStateException.class,
                () -> controller.start(Selector.STAGED, null, "balanced"));
        launcher.event("{\"protocolVersion\":1,\"type\":\"confirmationRequired\","
                + "\"message\":\"Bounded source will be sent.\"}\n");
        controller.confirm(false);
        launcher.event("{\"protocolVersion\":1,\"type\":\"error\",\"code\":\"SAFE\","
                + "\"message\":\"Safe failure\",\"exitCode\":8}\n");
        controller.cancel();
        controller.dispose();

        assertTrue(launcher.requests.stream().anyMatch(line -> line.contains("\"accepted\":false")));
        assertFalse(view.confirmationEnabled);
        assertTrue(view.text.contains("Safe failure"));
        assertTrue(launcher.session.cancelled);
        assertFalse(view.active);
    }

    @Test
    void sendsThreadIdOnlyAfterCapabilityAndClearsPerRunConsent() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        var controller = controller(view, launcher);

        controller.preview(Selector.STAGED, null, "balanced", "private-thread-id", true);
        assertTrue(launcher.spec.provenanceRequested());
        assertTrue(launcher.requests.isEmpty());
        launcher.event("{\"protocolVersion\":1,\"type\":\"hello\","
                + "\"capabilities\":[\"interactiveDefenseV1\",\"codexProvenanceV1\"]}\n");
        launcher.event("{\"protocolVersion\":1,\"type\":\"provenance\","
                + "\"status\":\"Exact change match\",\"disclaimer\":\"Does not prove authorship.\"}\n");

        assertEquals(1, launcher.requests.size());
        assertTrue(launcher.requests.getFirst().contains("private-thread-id"));
        assertFalse(launcher.spec.toString().contains("private-thread-id"));
        assertTrue(view.provenanceCleared);
        assertTrue(view.text.stream().anyMatch(value -> value.contains("Exact change match")));
    }

    @Test
    void negotiatedProtocolControlsEveryOutboundRequest() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher(1);
        var controller = controller(view, launcher);

        controller.start(Selector.STAGED, null, "balanced", "private-thread-id", true);
        launcher.event("{\"protocolVersion\":1,\"type\":\"hello\","
                + "\"capabilities\":[\"codexProvenanceV1\"]}\n");
        launcher.event("{\"protocolVersion\":1,\"type\":\"confirmationRequired\","
                + "\"message\":\"Send bounded source?\"}\n");
        controller.confirm(true);
        controller.answer("bounded answer");
        controller.skip();

        assertEquals(4, launcher.requests.size());
        assertTrue(launcher.requests.stream().allMatch(
                request -> request.contains("\"protocolVersion\":1")));
        assertTrue(launcher.requests.stream().anyMatch(request -> request.contains("provenanceConsent")));
        assertTrue(launcher.requests.stream().anyMatch(request -> request.contains("\"type\":\"confirm\"")));
        assertTrue(launcher.requests.stream().anyMatch(request -> request.contains("\"type\":\"answer\"")));
        assertTrue(launcher.requests.stream().anyMatch(request -> request.contains("\"type\":\"skip\"")));
    }

    @Test
    void cancelBeforeLauncherReturnsCancelsTheReturnedSessionWithoutPublishingIt() {
        FakeView view = new FakeView();
        SequencedLauncher launcher = new SequencedLauncher();
        List<Runnable> background = new ArrayList<>();
        var controller = lifecycleController(view, launcher, background::add);

        controller.start(Selector.STAGED, null, "balanced");
        controller.cancel();
        background.getFirst().run();

        assertTrue(launcher.sessions.getFirst().cancelled);
        assertFalse(view.active);
        assertThrows(IllegalStateException.class, () -> controller.answer("must not be accepted"));
    }

    @Test
    void oldCompletionAfterNewStartCannotDeactivateTheNewSession() {
        FakeView view = new FakeView();
        SequencedLauncher launcher = new SequencedLauncher();
        var controller = lifecycleController(view, launcher, Runnable::run);

        controller.start(Selector.STAGED, null, "balanced");
        ManualSession old = launcher.sessions.getFirst();
        controller.cancel();
        controller.start(Selector.STAGED, null, "testing");
        ManualSession current = launcher.sessions.get(1);

        old.completion.complete(0);
        controller.answer("current answer");

        assertTrue(view.active);
        assertTrue(current.requests.stream().anyMatch(request -> request.contains("current answer")));
    }

    @Test
    void lateOldEventsCannotReplaceEvidenceFromTheCurrentSession() {
        FakeView view = new FakeView();
        SequencedLauncher launcher = new SequencedLauncher();
        var controller = lifecycleController(view, launcher, Runnable::run);

        controller.start(Selector.STAGED, null, "balanced");
        controller.cancel();
        controller.start(Selector.STAGED, null, "testing");
        launcher.event(1, question(false, "src/Current.java", 4, 5));

        launcher.event(0, question(false, "src/Old.java", 1, 2));

        assertEquals(List.of(new EvidenceLocationView("src/Current.java", 4, 5)), view.evidence);
    }

    @Test
    void defendStagedChangeOnlyPreparesPreviewAndNeverLaunchesASession() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        var controller = controller(view, launcher);

        controller.defendStagedChange();

        assertTrue(view.stagedDefensePrepared);
        assertEquals(null, launcher.spec);
        assertTrue(launcher.requests.isEmpty());
    }

    @Test
    void manualRefreshAndPassportSavedRequestGateRefresh() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        int[] gateRefreshes = {0};
        var controller = new CodeDefenseToolWindowController(view, launcher, new BridgeLineCodec(),
                Runnable::run, Runnable::run, null, null, () -> gateRefreshes[0]++);

        controller.refresh();
        controller.start(Selector.STAGED, null, "balanced");
        launcher.event("{\"protocolVersion\":1,\"type\":\"passportSaved\","+
                "\"path\":\"C:/safe/passport.md\",\"status\":\"CURRENT\","+
                "\"shortFingerprint\":\"abc123\"}\n");

        assertEquals(2, gateRefreshes[0]);
    }

    private CodeDefenseToolWindowController controller(FakeView view, FakeLauncher launcher) {
        return new CodeDefenseToolWindowController(view, launcher, new BridgeLineCodec(), Runnable::run);
    }

    private CodeDefenseToolWindowController lifecycleController(FakeView view,
            CodeDefenseToolWindowController.SessionLauncher launcher, java.util.concurrent.Executor background) {
        return new CodeDefenseToolWindowController(view, launcher, new BridgeLineCodec(), Runnable::run,
                background, null, null, () -> { }, location ->
                        new EvidenceNavigator.NavigationResult(
                                EvidenceNavigator.NavigationStatus.OPENED, "Opened."));
    }

    private static String question(boolean followUp, String path, int start, int end) {
        return "{\"protocolVersion\":2,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":" + followUp + ",\"prompt\":\"Explain\",\"evidence\":[{"
                + "\"relativePath\":\"" + path + "\",\"startLine\":" + start
                + ",\"endLine\":" + end + "}]}\n";
    }

    private static final class FakeLauncher implements CodeDefenseToolWindowController.SessionLauncher {
        private final List<String> requests = new ArrayList<>();
        private final FakeSession session;
        private Consumer<BridgeLineCodec.BridgeMessage> consumer;
        private BridgeLaunchSpec spec;

        private FakeLauncher() {
            this(1);
        }

        private FakeLauncher(int protocolVersion) {
            session = new FakeSession(requests, protocolVersion);
        }

        @Override
        public CodeDefenseToolWindowController.Session launch(BridgeLaunchSpec launchSpec,
                Consumer<BridgeLineCodec.BridgeMessage> eventConsumer) {
            spec = launchSpec;
            consumer = eventConsumer;
            return session;
        }

        void event(String json) {
            consumer.accept(new BridgeLineCodec().decodeEvent(json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static final class FakeSession implements CodeDefenseToolWindowController.Session {
        private final List<String> requests;
        private final CompletableFuture<Integer> completion = new CompletableFuture<>();
        private final int protocolVersion;
        private boolean cancelled;

        private FakeSession(List<String> requests, int protocolVersion) {
            this.requests = requests;
            this.protocolVersion = protocolVersion;
        }

        @Override public void confirm(boolean accepted) {
            record(new BridgeLineCodec(protocolVersion).confirmRequest(accepted));
        }
        @Override public void answer(String answer) {
            record(new BridgeLineCodec(protocolVersion).answerRequest(answer));
        }
        @Override public void skip() {
            record(new BridgeLineCodec(protocolVersion).skipRequest());
        }
        @Override public void provenanceConsent(String threadId, boolean consent) {
            record(new BridgeLineCodec(protocolVersion).provenanceConsentRequest(threadId, consent));
        }
        @Override public void cancel() {
            cancelled = true;
            completion.complete(130);
        }
        @Override public CompletableFuture<Integer> completion() { return completion; }
        private void record(byte[] request) {
            requests.add(new String(request, StandardCharsets.UTF_8));
        }
    }

    private static final class SequencedLauncher implements CodeDefenseToolWindowController.SessionLauncher {
        private final List<ManualSession> sessions = new ArrayList<>();
        private final List<Consumer<BridgeLineCodec.BridgeMessage>> consumers = new ArrayList<>();

        @Override
        public CodeDefenseToolWindowController.Session launch(BridgeLaunchSpec launchSpec,
                Consumer<BridgeLineCodec.BridgeMessage> eventConsumer) {
            ManualSession session = new ManualSession();
            sessions.add(session);
            consumers.add(eventConsumer);
            return session;
        }

        private void event(int index, String json) {
            consumers.get(index).accept(new BridgeLineCodec().decodeEvent(
                    json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static final class ManualSession implements CodeDefenseToolWindowController.Session {
        private final List<String> requests = new ArrayList<>();
        private final CompletableFuture<Integer> completion = new CompletableFuture<>();
        private boolean cancelled;

        @Override public void confirm(boolean accepted) { record(new BridgeLineCodec().confirmRequest(accepted)); }
        @Override public void answer(String answer) { record(new BridgeLineCodec().answerRequest(answer)); }
        @Override public void skip() { record(new BridgeLineCodec().skipRequest()); }
        @Override public void provenanceConsent(String threadId, boolean consent) {
            record(new BridgeLineCodec().provenanceConsentRequest(threadId, consent));
        }
        @Override public void cancel() { cancelled = true; }
        @Override public CompletableFuture<Integer> completion() { return completion; }

        private void record(byte[] request) {
            requests.add(new String(request, StandardCharsets.UTF_8));
        }
    }

    private static final class FakeView implements CodeDefenseToolWindowView {
        private final List<String> text = new ArrayList<>();
        private boolean active;
        private boolean confirmationShown;
        private boolean answerCleared;
        private String passportPath;
        private boolean provenanceCleared;
        private boolean confirmationEnabled;
        private boolean stagedDefensePrepared;
        private List<EvidenceLocationView> evidence = List.of();
        private java.util.function.Function<EvidenceLocationView, EvidenceNavigator.NavigationResult>
                evidenceOpener;

        @Override public void setSessionActive(boolean value) { active = value; }
        @Override public void setConfirmationEnabled(boolean value) { confirmationEnabled = value; }
        @Override public void showPreview(String value) { text.add(value); }
        @Override public void showConfirmation(String value) { confirmationShown = true; text.add(value); }
        @Override public void showQuestion(String value) { text.add(value); }
        @Override public void showEvaluation(String value) { text.add(value); }
        @Override public void showQuestionScore(String value) { text.add(value); }
        @Override public void showSummary(String value) { text.add(value); }
        @Override public void showPassportSaved(String path, String value) { passportPath = path; text.add(value); }
        @Override public void showCompleted(String value) { text.add(value); }
        @Override public void showError(String value) { text.add(value); }
        @Override public void clearAnswer() { answerCleared = true; }
        @Override public void clearProvenanceConsent() { provenanceCleared = true; }
        @Override public void showProvenance(String value) { text.add(value); }
        @Override public void prepareStagedDefense() { stagedDefensePrepared = true; }
        @Override public void showEvidence(List<EvidenceLocationView> locations,
                java.util.function.Function<EvidenceLocationView, EvidenceNavigator.NavigationResult> opener) {
            evidence = List.copyOf(locations);
            evidenceOpener = opener;
        }
        @Override public void clearEvidence() { evidence = List.of(); evidenceOpener = null; }

        private EvidenceNavigator.NavigationResult openEvidence(int index) {
            return evidenceOpener.apply(evidence.get(index));
        }
    }
}
