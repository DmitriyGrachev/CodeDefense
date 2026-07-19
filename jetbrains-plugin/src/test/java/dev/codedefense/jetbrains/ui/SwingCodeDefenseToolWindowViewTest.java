package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.jetbrains.gate.StagedGateView;
import dev.codedefense.jetbrains.evidence.EvidenceNavigator;
import dev.codedefense.jetbrains.process.EvidenceLocationView;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class SwingCodeDefenseToolWindowViewTest {
    @Test
    void evidenceItemsAreAccessibleReplaceableAndContainOnlyPathAndRange() {
        var view = new SwingCodeDefenseToolWindowView();
        int[] opens = {0};
        var first = new EvidenceLocationView("src/First.java", 4, 9);
        var second = new EvidenceLocationView("src/Second.java", 12, 12);

        view.showEvidence(List.of(first, second), location -> {
            opens[0]++;
            return new EvidenceNavigator.NavigationResult(
                    EvidenceNavigator.NavigationStatus.OPENED, "Evidence opened.");
        });

        JButton firstButton = find(view.component(), JButton.class, "src/First.java:4–9");
        assertTrue(firstButton.isFocusable());
        assertEquals("Open evidence src/First.java lines 4 to 9",
                firstButton.getAccessibleContext().getAccessibleName());
        Object enterAction = firstButton.getInputMap(JComponent.WHEN_FOCUSED)
                .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        assertNotNull(enterAction);
        firstButton.getActionMap().get(enterAction).actionPerformed(
                new ActionEvent(firstButton, ActionEvent.ACTION_PERFORMED, "keyboard"));
        assertEquals(1, opens[0]);
        assertFalse(componentText(view.component()).contains("SOURCE-CONTENT"));

        view.showEvidence(List.of(second), ignored -> new EvidenceNavigator.NavigationResult(
                EvidenceNavigator.NavigationStatus.OPENED, "Evidence opened."));
        assertThrows(IllegalArgumentException.class,
                () -> find(view.component(), JButton.class, "src/First.java:4–9"));
        assertEquals(1, named(view.component(), JPanel.class, "codeDefense.evidencePanel")
                .getComponentCount());
    }

    @Test
    void unavailableEvidenceDisablesAfterOneAttemptAndShowsSafeMessage() {
        var view = new SwingCodeDefenseToolWindowView();
        int[] attempts = {0};
        view.showEvidence(List.of(new EvidenceLocationView("Deleted.java", 1, 1)), location -> {
            attempts[0]++;
            return new EvidenceNavigator.NavigationResult(
                    EvidenceNavigator.NavigationStatus.UNAVAILABLE, "Evidence file is unavailable.");
        });
        JButton button = find(view.component(), JButton.class, "Deleted.java:1");

        button.doClick();
        button.doClick();

        assertEquals(1, attempts[0]);
        assertFalse(button.isEnabled());
        assertTrue(componentText(view.component()).contains("Evidence file is unavailable."));
        view.clearEvidence();
        assertEquals(0, named(view.component(), JPanel.class, "codeDefense.evidencePanel")
                .getComponentCount());
    }

    @Test
    void unsafeEvidenceIsDisabledAndExplainsThatItWasNotOpened() {
        var view = new SwingCodeDefenseToolWindowView();
        view.showEvidence(List.of(new EvidenceLocationView("Linked.java", 1, 1)), location ->
                new EvidenceNavigator.NavigationResult(
                        EvidenceNavigator.NavigationStatus.UNSAFE, "Evidence path is unsafe."));
        JButton button = find(view.component(), JButton.class, "Linked.java:1");

        button.doClick();

        assertFalse(button.isEnabled());
        assertTrue(componentText(view.component()).contains("Unsafe evidence was not opened."));
    }

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

    private String componentText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JButton button) text.append(button.getText()).append('\n');
        if (component instanceof JLabel label) text.append(label.getText()).append('\n');
        if (component instanceof javax.swing.text.JTextComponent field) {
            text.append(field.getText()).append('\n');
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) text.append(componentText(child));
        }
        return text.toString();
    }
}
