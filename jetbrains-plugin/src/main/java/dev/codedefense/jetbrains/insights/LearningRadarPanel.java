package dev.codedefense.jetbrains.insights;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/** Accessible Swing rendering of source-free repository learning insights. */
public final class LearningRadarPanel extends JPanel {
    private static final Map<String, String> LABELS = Map.of(
            "decision", "Decision",
            "counterfactual", "Counterfactual",
            "test-prediction", "Test prediction");

    public LearningRadarPanel() {
        super();
        setName("codeDefense.learningRadar");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        showUnavailable();
    }

    public void showInsights(RepositoryInsightsView insights) {
        removeAll();
        add(heading());
        if (insights.attemptCount() == 0) {
            add(new JLabel("No completed defenses for this repository yet."));
        } else {
            for (CategoryInsightView category : insights.categories()) add(categoryRow(category));
            add(new JLabel("Recent overall: " + insights.recentOverallScores().stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining(" -> "))));
            add(new JLabel("Practice next: " + LABELS.get(insights.practiceCategory())));
        }
        refreshLayout();
    }

    public void showUnavailable() {
        removeAll();
        add(heading());
        add(new JLabel("Repository learning history is unavailable."));
        refreshLayout();
    }

    private JPanel categoryRow(CategoryInsightView category) {
        String label = LABELS.get(category.id());
        String value = label + ": " + category.averageScore() + "/100";
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setName("codeDefense.learningRadar." + category.id());
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(category.averageScore());
        bar.setString(value);
        bar.setStringPainted(true);
        bar.getAccessibleContext().setAccessibleName(value);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }

    private JPanel heading() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel heading = new JLabel("Repository-local defense history");
        heading.getAccessibleContext().setAccessibleName("Repository-local defense history");
        row.add(heading);
        return row;
    }

    private void refreshLayout() {
        revalidate();
        repaint();
    }
}
