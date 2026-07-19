package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
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
