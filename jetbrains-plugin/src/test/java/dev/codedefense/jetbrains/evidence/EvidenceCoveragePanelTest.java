package dev.codedefense.jetbrains.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class EvidenceCoveragePanelTest {
    @Test
    void groupsUncoveredHunksFirstAndKeepsSafeRowsClickable() {
        EvidenceCoveragePanel panel = new EvidenceCoveragePanel();
        EvidenceCoverageView coverage = new EvidenceCoverageView(2, 2, 1, List.of(
                new EvidenceCoverageView.Hunk("src/Referenced.java", 1, 10, 12, true,
                        "REFERENCED", List.of("decision")),
                new EvidenceCoverageView.Hunk("src/Uncovered.java", 1, 30, 31, true,
                        "UNREFERENCED", List.of())));
        AtomicInteger opens = new AtomicInteger();

        panel.showCoverage(coverage, location -> {
            opens.incrementAndGet();
            return new EvidenceNavigator.NavigationResult(EvidenceNavigator.NavigationStatus.OPENED, "opened");
        });

        List<String> labels = text(panel);
        assertTrue(labels.contains("Evidence Coverage 1 / 2 · 50%"));
        assertTrue(labels.indexOf("○ src/Uncovered.java · hunk 1 · lines 30–31 · Not referenced")
                < labels.indexOf("✓ src/Referenced.java · hunk 1 · lines 10–12 · Decision"));
        assertTrue(labels.contains("Evidence use only — not correctness or safety coverage."));
        buttons(panel).getFirst().doClick();
        assertEquals(1, opens.get());
    }

    private static List<String> text(Container root) {
        List<String> values = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel label) values.add(label.getText());
            if (component instanceof AbstractButton button) values.add(button.getText());
            if (component instanceof Container nested) values.addAll(text(nested));
        }
        return values;
    }

    private static List<AbstractButton> buttons(Container root) {
        List<AbstractButton> values = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button) values.add(button);
            if (component instanceof Container nested) values.addAll(buttons(nested));
        }
        return values;
    }
}
