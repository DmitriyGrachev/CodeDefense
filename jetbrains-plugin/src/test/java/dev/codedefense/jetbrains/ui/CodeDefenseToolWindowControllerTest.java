package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.jetbrains.process.BridgeLineCodec;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.BridgeLaunchSpec;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CodeDefenseToolWindowControllerTest {
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
    void declineErrorCancelDoubleStartAndDisposeAreSafe() {
        FakeView view = new FakeView();
        FakeLauncher launcher = new FakeLauncher();
        var controller = controller(view, launcher);
        controller.start(Selector.RANGE, "main..feature", "architecture");

        assertThrows(IllegalStateException.class,
                () -> controller.start(Selector.STAGED, null, "balanced"));
        controller.confirm(false);
        launcher.event("{\"protocolVersion\":1,\"type\":\"error\",\"code\":\"SAFE\","
                + "\"message\":\"Safe failure\",\"exitCode\":8}\n");
        controller.cancel();
        controller.dispose();

        assertTrue(launcher.requests.stream().anyMatch(line -> line.contains("\"accepted\":false")));
        assertTrue(view.text.contains("Safe failure"));
        assertTrue(launcher.session.cancelled);
        assertFalse(view.active);
    }

    private CodeDefenseToolWindowController controller(FakeView view, FakeLauncher launcher) {
        return new CodeDefenseToolWindowController(view, launcher, new BridgeLineCodec(), Runnable::run);
    }

    private static final class FakeLauncher implements CodeDefenseToolWindowController.SessionLauncher {
        private final List<String> requests = new ArrayList<>();
        private final FakeSession session = new FakeSession(requests);
        private Consumer<BridgeLineCodec.BridgeMessage> consumer;
        private BridgeLaunchSpec spec;

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
        private boolean cancelled;

        private FakeSession(List<String> requests) {
            this.requests = requests;
        }

        @Override public void send(byte[] request) {
            requests.add(new String(request, StandardCharsets.UTF_8));
        }
        @Override public void cancel() {
            cancelled = true;
            completion.complete(130);
        }
        @Override public CompletableFuture<Integer> completion() { return completion; }
    }

    private static final class FakeView implements CodeDefenseToolWindowView {
        private final List<String> text = new ArrayList<>();
        private boolean active;
        private boolean confirmationShown;
        private boolean answerCleared;
        private String passportPath;

        @Override public void setSessionActive(boolean value) { active = value; }
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
    }
}
