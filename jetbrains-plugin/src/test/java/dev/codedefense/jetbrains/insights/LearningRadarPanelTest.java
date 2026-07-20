package dev.codedefense.jetbrains.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import org.junit.jupiter.api.Test;

class LearningRadarPanelTest {
    @Test
    void rendersAccessibleCategoryBarsRecentTrendAndPracticeCategory() {
        LearningRadarPanel panel = new LearningRadarPanel();

        panel.showInsights(insights());

        String text = componentText(panel);
        assertTrue(text.contains("Repository-local defense history"));
        assertTrue(text.contains("Decision: 92/100"));
        assertTrue(text.contains("Counterfactual: 54/100"));
        assertTrue(text.contains("Test prediction: 31/100"));
        assertTrue(text.contains("Recent overall: 33 -> 61 -> 84"));
        assertTrue(text.contains("Practice next: Test prediction"));
        List<JProgressBar> bars = descendants(panel, JProgressBar.class);
        assertEquals(3, bars.size());
        assertTrue(bars.stream().allMatch(JProgressBar::isStringPainted));
        assertTrue(bars.stream().allMatch(bar -> bar.getAccessibleContext().getAccessibleName() != null));
        assertFalse(text.contains("PRIVATE-ANSWER"));
        assertFalse(text.contains("QUESTION-MARKER"));
        assertFalse(text.contains("FEEDBACK-MARKER"));
        assertFalse(text.contains("C:\\private\\project"));
    }

    @Test
    void emptyHistoryShowsOnlyTheDocumentedEmptyState() {
        LearningRadarPanel panel = new LearningRadarPanel();
        RepositoryInsightsView empty = new RepositoryInsightsView(1, 0, 0, List.of(
                new CategoryInsightView("decision", 0),
                new CategoryInsightView("counterfactual", 0),
                new CategoryInsightView("test-prediction", 0)), "", "", List.of());

        panel.showInsights(empty);

        String text = componentText(panel);
        assertTrue(text.contains("Repository-local defense history"));
        assertTrue(text.contains("No completed defenses for this repository yet."));
        assertTrue(descendants(panel, JProgressBar.class).isEmpty());
    }

    @Test
    void unavailableStateRemovesPreviouslyRenderedScores() {
        LearningRadarPanel panel = new LearningRadarPanel();
        panel.showInsights(insights());

        panel.showUnavailable();

        String text = componentText(panel);
        assertTrue(text.contains("Repository learning history is unavailable."));
        assertFalse(text.contains("92/100"));
        assertFalse(text.contains("Recent overall"));
        assertTrue(descendants(panel, JProgressBar.class).isEmpty());
    }

    private RepositoryInsightsView insights() {
        return new RepositoryInsightsView(1, 3, 2, List.of(
                new CategoryInsightView("decision", 92),
                new CategoryInsightView("counterfactual", 54),
                new CategoryInsightView("test-prediction", 31)),
                "decision", "test-prediction", List.of(33, 61, 84));
    }

    private String componentText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JLabel label) text.append(label.getText()).append('\n');
        if (component instanceof JProgressBar bar && bar.getString() != null) text.append(bar.getString()).append('\n');
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) text.append(componentText(child));
        }
        return text.toString();
    }

    private <T extends Component> List<T> descendants(Component component, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(component)) matches.add(type.cast(component));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) matches.addAll(descendants(child, type));
        }
        return matches;
    }
}
