package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.jetbrains.gate.StagedGateView;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class SwingCodeDefenseToolWindowViewTest {
    @Test
    void actionButtonsUseASeparateRowFromChangeSelectors() {
        var view = new SwingCodeDefenseToolWindowView();

        JButton preview = find(view.component(), JButton.class, "Preview defense");
        JButton start = find(view.component(), JButton.class, "Start defense");
        JLabel change = find(view.component(), JLabel.class, "Change:");

        assertSame(preview.getParent(), start.getParent());
        assertNotSame(change.getParent(), preview.getParent());
    }

    @Test
    void rendersAccessibleTextForEveryGateStateWithoutUsingColorAsTheOnlySignal() {
        var view = new SwingCodeDefenseToolWindowView();
        JLabel badge = named(view.component(), JLabel.class, "codeDefense.gateBadge");

        for (StagedGateView gate : List.of(
                gate(StagedGateView.State.NO_STAGED_CHANGE, StagedGateView.Reason.NO_INDEX_ENTRIES, "", 0, 0, 0, 0),
                gate(StagedGateView.State.UNDEFENDED, StagedGateView.Reason.NO_STAGED_HISTORY, "a".repeat(64), 0, 2, 7, 3),
                gate(StagedGateView.State.CURRENT, StagedGateView.Reason.IDENTITY_MATCH, "b".repeat(64), 2, 2, 7, 3),
                gate(StagedGateView.State.EXPIRED, StagedGateView.Reason.IDENTITY_CHANGED, "c".repeat(64), 0, 2, 7, 3),
                gate(StagedGateView.State.UNAVAILABLE, StagedGateView.Reason.GIT_CAPTURE_FAILED, "", 0, 0, 0, 0))) {
            view.showGateStatus(gate);

            assertEquals(gate.state().name().replace('_', ' '), badge.getText());
            assertTrue(badge.getAccessibleContext().getAccessibleName().contains(badge.getText()));
        }
    }

    @Test
    void rendersOnlyCountsAndCurrentShortFingerprintAndNeverPathsOrFullFingerprint() {
        var view = new SwingCodeDefenseToolWindowView();
        String fingerprint = "0123456789abcdef".repeat(4);
        var current = new StagedGateView(1, StagedGateView.State.CURRENT,
                StagedGateView.Reason.IDENTITY_MATCH, fingerprint, 1, 3, 12, 4, List.of());

        view.showGateStatus(current);

        JLabel summary = named(view.component(), JLabel.class, "codeDefense.gateSummary");
        assertTrue(summary.getText().contains("3 staged files"));
        assertTrue(summary.getText().contains("+12/-4"));
        assertTrue(summary.getText().contains("0123456789ab"));
        assertFalse(summary.getText().contains(fingerprint));
        assertFalse(view.component().toString().contains("src/private/Secret.java"));

        var expired = new StagedGateView(1, StagedGateView.State.EXPIRED,
                StagedGateView.Reason.IDENTITY_CHANGED, fingerprint, 0, 3, 12, 4,
                List.of("src/private/Secret.java"));
        view.showGateStatus(expired);
        assertFalse(summary.getText().contains("Secret.java"));
        assertFalse(summary.getText().contains("0123456789ab"));
    }

    @Test
    void preparingStagedDefenseSelectsStagedAndEnablesPreviewWithoutClickingIt() {
        var view = new SwingCodeDefenseToolWindowView();

        view.prepareStagedDefense();

        JComboBox<?> selector = named(view.component(), JComboBox.class, "codeDefense.changeSelector");
        JButton preview = find(view.component(), JButton.class, "Preview defense");
        assertEquals(dev.codedefense.jetbrains.process.CodeDefenseLauncher.Selector.STAGED,
                selector.getSelectedItem());
        assertTrue(preview.isEnabled());
    }

    private StagedGateView gate(StagedGateView.State state, StagedGateView.Reason reason,
            String fingerprint, int attempt, int files, int added, int deleted) {
        return new StagedGateView(1, state, reason, fingerprint, attempt, files, added, deleted, List.of());
    }

    private <T extends Component> T named(Component component, Class<T> type, String name) {
        if (type.isInstance(component) && name.equals(component.getName())) return type.cast(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                try { return named(child, type, name); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Component not found: " + name);
    }

    private <T extends Component> T find(Component component, Class<T> type, String text) {
        if (type.isInstance(component) && text.equals(label(component))) {
            return type.cast(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return find(child, type, text);
                } catch (IllegalArgumentException ignored) {
                    // Continue searching this component tree.
                }
            }
        }
        throw new IllegalArgumentException("Component not found: " + text);
    }

    private String label(Component component) {
        if (component instanceof JButton button) return button.getText();
        if (component instanceof JLabel label) return label.getText();
        return null;
    }
}
