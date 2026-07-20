package dev.codedefense.jetbrains.commit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.jetbrains.gate.StagedGateView;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.Test;

class PassportTrailerCommitOptionTest {
    @Test
    void startsUncheckedAndDisabledThenEnablesOnlyAfterFreshCurrentPreflight() {
        ManualExecutor background = new ManualExecutor();
        ManualExecutor ui = new ManualExecutor();
        Duration[] observedTimeout = new Duration[1];
        var option = new PassportTrailerCommitOption(timeout -> {
            observedTimeout[0] = timeout;
            return current();
        }, background, ui);

        option.getComponent();

        assertFalse(option.isSelected());
        assertFalse(option.isEnabled());
        assertTrue(background.hasTask());

        background.runNext();
        assertFalse(option.isEnabled());
        ui.runNext();

        assertTrue(option.isEnabled());
        assertTrue(observedTimeout[0].isPositive());
        assertFalse(option.isSelected());
    }

    @Test
    void nonCurrentOrFailedPreflightNeverEnablesConsent() {
        for (PassportTrailerCommitOption.Gate gate : List.of(
                (PassportTrailerCommitOption.Gate) timeout -> expired(),
                (PassportTrailerCommitOption.Gate) timeout -> {
                    throw new IllegalStateException("private detail");
                })) {
            ManualExecutor background = new ManualExecutor();
            ManualExecutor ui = new ManualExecutor();
            var option = new PassportTrailerCommitOption(gate, background, ui);

            option.getComponent();
            background.runNext();
            ui.runNext();

            assertFalse(option.isEnabled());
            assertFalse(option.isSelected());
        }
    }

    @Test
    void eachOptionInstanceStartsUncheckedAndRestoreNeverPersistsSelection() {
        PassportTrailerCommitOption first = currentOption();
        ((JCheckBox) first.getComponent()).setSelected(true);
        assertTrue(first.isSelected());

        first.saveState();
        first.restoreState();
        assertFalse(first.isSelected());

        PassportTrailerCommitOption next = currentOption();
        assertFalse(next.isSelected());
    }

    private static PassportTrailerCommitOption currentOption() {
        ManualExecutor background = new ManualExecutor();
        ManualExecutor ui = new ManualExecutor();
        var option = new PassportTrailerCommitOption(timeout -> current(), background, ui);
        option.getComponent();
        background.runNext();
        ui.runNext();
        return option;
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

    private static final class ManualExecutor implements PassportTrailerCommitOption.TaskExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable task) { tasks.addLast(task); }
        boolean hasTask() { return !tasks.isEmpty(); }
        void runNext() { tasks.removeFirst().run(); }
    }
}
