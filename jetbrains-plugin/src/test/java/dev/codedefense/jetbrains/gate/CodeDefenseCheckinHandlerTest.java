package dev.codedefense.jetbrains.gate;

import static com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult.CANCEL;
import static com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult.COMMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javax.swing.JCheckBox;

import com.intellij.openapi.progress.ProcessCanceledException;
import dev.codedefense.jetbrains.commit.PassportTrailerCommitOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CodeDefenseCheckinHandlerTest {
    @Test
    void cachedCurrentNeverApprovesWhenFreshStateIsExpired() {
        FakeGate gate = new FakeGate(expired());
        FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.CANCEL);
        var handler = handler(CommitModeDetector.Mode.STAGED_ONLY, gate, prompt, () -> { });

        assertEquals(CANCEL, handler.beforeCheckin());
        assertEquals(1, gate.calls);
        assertSame(expired().state(), prompt.states.getFirst().state());
    }

    @Test
    void freshCurrentCommitsWithoutPrompt() {
        FakeGate gate = new FakeGate(current());
        FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.CANCEL);

        assertEquals(COMMIT, handler(CommitModeDetector.Mode.STAGED_ONLY, gate, prompt, () -> { })
                .beforeCheckin());
        assertEquals(1, gate.calls);
        assertEquals(List.of(), prompt.states);
    }

    @Test
    void everyNonCurrentFreshStatePromptsSafely() {
        for (StagedGateView state : List.of(noChange(), undefended(), expired(), unavailable())) {
            FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.CANCEL);

            assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY,
                    new FakeGate(state), prompt, () -> { }).beforeCheckin());
            assertEquals(List.of(state), prompt.states);
        }
    }

    @Test
    void defendCancelsCommitAndOnlyNavigatesToStagedPreview() {
        int[] navigations = {0};
        FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.DEFEND);

        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY,
                new FakeGate(expired()), prompt, () -> navigations[0]++).beforeCheckin());
        assertEquals(1, navigations[0]);
    }

    @Test
    void commitAnywayAppliesOnlyToOneCallbackAndIsNeverPersisted() {
        FakeGate gate = new FakeGate(expired(), expired());
        FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.COMMIT_ANYWAY);
        var handler = handler(CommitModeDetector.Mode.STAGED_ONLY, gate, prompt, () -> { });

        assertEquals(COMMIT, handler.beforeCheckin());
        assertEquals(COMMIT, handler.beforeCheckin());
        assertEquals(2, gate.calls);
        assertEquals(2, prompt.states.size());
    }

    @Test
    void cancelCancelsCommit() {
        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY,
                new FakeGate(undefended()), new FakePrompt(CommitGatePrompt.Decision.CANCEL),
                () -> { }).beforeCheckin());
    }

    @Test
    void timeoutMalformedOrFailureBecomesUnavailableAndPrompts() {
        FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.CANCEL);
        CodeDefenseCheckinHandler.FreshGate failing = timeout -> {
            throw new IllegalStateException("raw failure must not escape");
        };

        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY,
                failing, prompt, () -> { }).beforeCheckin());
        assertEquals(StagedGateView.State.UNAVAILABLE, prompt.states.getFirst().state());
    }

    @Test
    void progressCancellationCancelsWithoutPrompt() {
        FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.COMMIT_ANYWAY);
        CodeDefenseCheckinHandler.ProgressRunner canceled = operation -> {
            throw new ProcessCanceledException();
        };

        var handler = new CodeDefenseCheckinHandler(
                () -> CommitModeDetector.Mode.STAGED_ONLY,
                new FakeGate(current()), prompt, canceled, () -> { });

        assertEquals(CANCEL, handler.beforeCheckin());
        assertEquals(List.of(), prompt.states);
    }

    @Test
    void freshCallbackObservesIndexMutationBetweenAttempts() {
        FakeGate gate = new FakeGate(current(), expired());
        FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.CANCEL);
        var handler = handler(CommitModeDetector.Mode.STAGED_ONLY, gate, prompt, () -> { });

        assertEquals(COMMIT, handler.beforeCheckin());
        assertEquals(CANCEL, handler.beforeCheckin());
        assertEquals(List.of(expired()), prompt.states);
    }

    @Test
    void unsupportedCommitModeOffersOnlyCommitAnywayOrCancelAndNeverChecksStagedGate() {
        for (CommitGatePrompt.Decision decision : List.of(
                CommitGatePrompt.Decision.COMMIT_ANYWAY, CommitGatePrompt.Decision.CANCEL)) {
            FakeGate gate = new FakeGate(current());
            FakePrompt prompt = new FakePrompt(decision);
            int[] navigations = {0};

            var result = handler(CommitModeDetector.Mode.UNSUPPORTED, gate, prompt,
                    () -> navigations[0]++).beforeCheckin();

            assertEquals(decision == CommitGatePrompt.Decision.COMMIT_ANYWAY ? COMMIT : CANCEL, result);
            assertEquals(0, gate.calls);
            assertEquals(1, prompt.unsupportedCalls);
            assertEquals(List.of(), prompt.states);
            assertEquals(0, navigations[0]);
        }
    }

    @Test
    void uncheckedCurrentConsentLeavesCommitMessageByteForByteUnchanged() {
        FakeGate gate = new FakeGate(current());
        FakeCommitMessage message = new FakeCommitMessage("subject\r\n\r\nbody");

        assertEquals(COMMIT, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                uncheckedOption(), message, new FakeNotice()).beforeCheckin());

        assertEquals("subject\r\n\r\nbody", message.value);
        assertEquals(0, message.setCalls);
        assertEquals(1, gate.calls);
    }

    @Test
    void checkedCurrentConsentAppendsExactFullFingerprintAfterSecondFreshCheck() {
        FakeGate gate = new FakeGate(current(), current());
        FakeCommitMessage message = new FakeCommitMessage("subject");

        assertEquals(COMMIT, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                checkedOption(), message, new FakeNotice()).beforeCheckin());

        assertEquals("subject\n\nCodeDefense-Passport: sha256:" + "a".repeat(64), message.value);
        assertEquals(1, message.setCalls);
        assertEquals(2, gate.calls);
    }

    @Test
    void sameTrailerIsAcceptedWithoutDuplicationOrMutation() {
        FakeGate gate = new FakeGate(current(), current());
        String original = "subject\n\nCodeDefense-Passport: sha256:" + "a".repeat(64);
        FakeCommitMessage message = new FakeCommitMessage(original);

        assertEquals(COMMIT, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                checkedOption(), message, new FakeNotice()).beforeCheckin());

        assertEquals(original, message.value);
        assertEquals(0, message.setCalls);
        assertEquals(2, gate.calls);
    }

    @Test
    void conflictingTrailerCancelsWithoutMutationAndShowsSafeAction() {
        FakeGate gate = new FakeGate(current());
        String original = "subject\n\nCodeDefense-Passport: sha256:" + "b".repeat(64);
        FakeCommitMessage message = new FakeCommitMessage(original);
        FakeNotice notice = new FakeNotice();

        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                checkedOption(), message, notice).beforeCheckin());

        assertEquals(original, message.value);
        assertEquals(0, message.setCalls);
        assertEquals(1, notice.conflicts);
        assertEquals(0, notice.stale);
        assertFalse(notice.text.contains("a".repeat(64)));
        assertFalse(notice.text.contains("b".repeat(64)));
    }

    @Test
    void indexMutationBeforeFinalCheckCancelsAndAppendsNothing() {
        FakeGate gate = new FakeGate(current(), expired());
        FakeCommitMessage message = new FakeCommitMessage("subject");
        FakeNotice notice = new FakeNotice();

        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                checkedOption(), message, notice).beforeCheckin());

        assertEquals("subject", message.value);
        assertEquals(0, message.setCalls);
        assertEquals(1, notice.stale);
    }

    @Test
    void differentCurrentFingerprintBeforeFinalCheckCancelsWithoutMutation() {
        FakeGate gate = new FakeGate(current(), current("c"));
        FakeCommitMessage message = new FakeCommitMessage("subject");
        FakeNotice notice = new FakeNotice();

        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                checkedOption(), message, notice).beforeCheckin());

        assertEquals("subject", message.value);
        assertEquals(0, message.setCalls);
        assertEquals(1, notice.stale);
    }

    @Test
    void commitMessageMutationDuringSecondCheckCancelsBeforeAddingTrailer() {
        FakeGate gate = new FakeGate(current(), current());
        FakeCommitMessage message = new FakeCommitMessage("subject");
        FakeNotice notice = new FakeNotice();
        CodeDefenseCheckinHandler.ProgressRunner progress = mutatingSecondProgress(
                message, "subject edited while checking");

        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                checkedOption(), message, notice, progress).beforeCheckin());

        assertEquals("subject edited while checking", message.value);
        assertEquals(0, message.setCalls);
        assertEquals(1, notice.stale);
    }

    @Test
    void commitMessageMutationDuringSecondCheckCancelsEvenWhenTrailerWasAlreadyPresent() {
        FakeGate gate = new FakeGate(current(), current());
        String original = "subject\n\nCodeDefense-Passport: sha256:" + "a".repeat(64);
        FakeCommitMessage message = new FakeCommitMessage(original);
        FakeNotice notice = new FakeNotice();
        CodeDefenseCheckinHandler.ProgressRunner progress = mutatingSecondProgress(
                message, original + "\nuser edit");

        assertEquals(CANCEL, handler(CommitModeDetector.Mode.STAGED_ONLY, gate,
                new FakePrompt(CommitGatePrompt.Decision.CANCEL), () -> { },
                checkedOption(), message, notice, progress).beforeCheckin());

        assertEquals(original + "\nuser edit", message.value);
        assertEquals(0, message.setCalls);
        assertEquals(1, notice.stale);
    }

    @Test
    void nonCurrentAndUnsupportedOverridePathsNeverAppendTrailer() {
        for (var scenario : List.of(
                new Scenario(CommitModeDetector.Mode.STAGED_ONLY, expired()),
                new Scenario(CommitModeDetector.Mode.UNSUPPORTED, current()))) {
            FakeGate gate = new FakeGate(scenario.state);
            FakeCommitMessage message = new FakeCommitMessage("subject");
            FakePrompt prompt = new FakePrompt(CommitGatePrompt.Decision.COMMIT_ANYWAY);

            assertEquals(COMMIT, handler(scenario.mode, gate, prompt, () -> { },
                    checkedOption(), message, new FakeNotice()).beforeCheckin());
            assertEquals("subject", message.value);
            assertEquals(0, message.setCalls);
        }
    }

    @Test
    void newHandlerStartsUncheckedAfterPreviousHandlerSelectedConsent() {
        PassportTrailerCommitOption selected = checkedOption();
        assertTrue(selected.isSelected());

        PassportTrailerCommitOption next = uncheckedOption();
        assertFalse(next.isSelected());
    }

    private CodeDefenseCheckinHandler handler(CommitModeDetector.Mode mode,
            CodeDefenseCheckinHandler.FreshGate gate, CommitGatePrompt prompt, Runnable navigator) {
        return handler(mode, gate, prompt, navigator, uncheckedOption(),
                new FakeCommitMessage("subject"), new FakeNotice());
    }

    private CodeDefenseCheckinHandler handler(CommitModeDetector.Mode mode,
            CodeDefenseCheckinHandler.FreshGate gate, CommitGatePrompt prompt, Runnable navigator,
            PassportTrailerCommitOption option, CodeDefenseCheckinHandler.CommitMessage message,
            CodeDefenseCheckinHandler.TrailerNotice notice) {
        return handler(mode, gate, prompt, navigator, option, message, notice, Supplier::get);
    }

    private CodeDefenseCheckinHandler handler(CommitModeDetector.Mode mode,
            CodeDefenseCheckinHandler.FreshGate gate, CommitGatePrompt prompt, Runnable navigator,
            PassportTrailerCommitOption option, CodeDefenseCheckinHandler.CommitMessage message,
            CodeDefenseCheckinHandler.TrailerNotice notice,
            CodeDefenseCheckinHandler.ProgressRunner progress) {
        return new CodeDefenseCheckinHandler(() -> mode, gate, prompt, progress, navigator,
                option, message, notice);
    }

    private CodeDefenseCheckinHandler.ProgressRunner mutatingSecondProgress(
            FakeCommitMessage message, String replacement) {
        int[] calls = {0};
        return operation -> {
            StagedGateView result = operation.get();
            if (++calls[0] == 2) message.editExternally(replacement);
            return result;
        };
    }

    private PassportTrailerCommitOption uncheckedOption() {
        return new PassportTrailerCommitOption(timeout -> expired(), Runnable::run, Runnable::run);
    }

    private PassportTrailerCommitOption checkedOption() {
        PassportTrailerCommitOption option = new PassportTrailerCommitOption(
                timeout -> current(), Runnable::run, Runnable::run);
        JCheckBox checkBox = (JCheckBox) option.getComponent();
        checkBox.setSelected(true);
        return option;
    }

    private static StagedGateView noChange() {
        return new StagedGateView(1, StagedGateView.State.NO_STAGED_CHANGE,
                StagedGateView.Reason.NO_INDEX_ENTRIES, "", 0, 0, 0, 0, List.of());
    }

    private static StagedGateView undefended() {
        return new StagedGateView(1, StagedGateView.State.UNDEFENDED,
                StagedGateView.Reason.NO_STAGED_HISTORY, "a".repeat(64), 0, 1, 2, 3, List.of());
    }

    private static StagedGateView current() {
        return current("a");
    }

    private static StagedGateView current(String digit) {
        return new StagedGateView(1, StagedGateView.State.CURRENT,
                StagedGateView.Reason.IDENTITY_MATCH, digit.repeat(64), 1, 1, 2, 3, List.of());
    }

    private static StagedGateView expired() {
        return new StagedGateView(1, StagedGateView.State.EXPIRED,
                StagedGateView.Reason.IDENTITY_CHANGED, "b".repeat(64), 0, 1, 2, 3,
                List.of("src/Changed.java"));
    }

    private static StagedGateView unavailable() {
        return new StagedGateView(1, StagedGateView.State.UNAVAILABLE,
                StagedGateView.Reason.GIT_CAPTURE_FAILED, "", 0, 0, 0, 0, List.of());
    }

    private static final class FakeGate implements CodeDefenseCheckinHandler.FreshGate {
        private final ArrayDeque<StagedGateView> results;
        private int calls;

        private FakeGate(StagedGateView... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override public StagedGateView fresh(Duration timeout) {
            calls++;
            if (timeout.isZero() || timeout.isNegative()) throw new AssertionError("timeout must be positive");
            return results.removeFirst();
        }
    }

    private static final class FakePrompt implements CommitGatePrompt {
        private final Decision decision;
        private final List<StagedGateView> states = new ArrayList<>();
        private int unsupportedCalls;

        private FakePrompt(Decision decision) {
            this.decision = decision;
        }

        @Override public Decision ask(StagedGateView state) {
            states.add(state);
            return decision;
        }

        @Override public Decision askUnsupportedCommitMode() {
            unsupportedCalls++;
            if (decision == Decision.DEFEND) throw new AssertionError("unsupported mode cannot offer Defend");
            return decision;
        }
    }

    private static final class FakeCommitMessage implements CodeDefenseCheckinHandler.CommitMessage {
        private String value;
        private int setCalls;

        private FakeCommitMessage(String value) { this.value = value; }
        @Override public String get() { return value; }
        @Override public void set(String value) { this.value = value; setCalls++; }
        private void editExternally(String value) { this.value = value; }
    }

    private static final class FakeNotice implements CodeDefenseCheckinHandler.TrailerNotice {
        private int conflicts;
        private int stale;
        private String text = "";

        @Override public void conflict(String message) { conflicts++; text = message; }
        @Override public void stale(String message) { stale++; text = message; }
    }

    private record Scenario(CommitModeDetector.Mode mode, StagedGateView state) { }
}
