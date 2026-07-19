package dev.codedefense.jetbrains.gate;

import static com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult.CANCEL;
import static com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult.COMMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.intellij.openapi.progress.ProcessCanceledException;
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

    private CodeDefenseCheckinHandler handler(CommitModeDetector.Mode mode,
            CodeDefenseCheckinHandler.FreshGate gate, CommitGatePrompt prompt, Runnable navigator) {
        return new CodeDefenseCheckinHandler(() -> mode, gate, prompt, Supplier::get, navigator);
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
        return new StagedGateView(1, StagedGateView.State.CURRENT,
                StagedGateView.Reason.IDENTITY_MATCH, "a".repeat(64), 1, 1, 2, 3, List.of());
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
}
