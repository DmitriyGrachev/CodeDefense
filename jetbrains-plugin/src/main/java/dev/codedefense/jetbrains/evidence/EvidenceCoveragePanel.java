package dev.codedefense.jetbrains.evidence;

import java.awt.BorderLayout;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class EvidenceCoveragePanel extends JPanel {
    public static final String DISCLAIMER = "Evidence use only — not correctness or safety coverage.";
    private final JLabel heading = new JLabel("Evidence Coverage");
    private final JPanel rows = new JPanel();

    public EvidenceCoveragePanel() {
        super(new BorderLayout(4, 4));
        setName("codeDefense.evidenceCoverage");
        getAccessibleContext().setAccessibleName("Evidence Coverage");
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        add(heading, BorderLayout.NORTH);
        add(rows, BorderLayout.CENTER);
        add(new JLabel(DISCLAIMER), BorderLayout.SOUTH);
        setVisible(false);
    }

    public void showCoverage(EvidenceCoverageView coverage,
            Function<dev.codedefense.jetbrains.process.EvidenceLocationView,
                    EvidenceNavigator.NavigationResult> opener) {
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(opener, "opener");
        String percent = coverage.percentage().isPresent()
                ? coverage.percentage().getAsInt() + "%" : "unavailable";
        heading.setText("Evidence Coverage " + coverage.referencedHunks() + " / "
                + coverage.measurableHunks() + " · " + percent);
        rows.removeAll();
        coverage.hunks().stream()
                .sorted(Comparator.comparingInt((EvidenceCoverageView.Hunk value) -> order(value.state()))
                        .thenComparing(EvidenceCoverageView.Hunk::relativePath)
                        .thenComparingInt(EvidenceCoverageView.Hunk::ordinal))
                .forEach(hunk -> rows.add(row(hunk, opener)));
        setVisible(true);
        revalidate(); repaint();
    }

    public void clearCoverage() {
        rows.removeAll();
        setVisible(false);
        revalidate(); repaint();
    }

    private JButton row(EvidenceCoverageView.Hunk hunk,
            Function<dev.codedefense.jetbrains.process.EvidenceLocationView,
                    EvidenceNavigator.NavigationResult> opener) {
        JButton button = new JButton(label(hunk));
        button.setHorizontalAlignment(JButton.LEFT);
        button.setEnabled(hunk.navigable());
        button.addActionListener(event -> {
            EvidenceNavigator.NavigationResult result;
            try { result = opener.apply(hunk.location()); }
            catch (RuntimeException exception) {
                result = new EvidenceNavigator.NavigationResult(
                        EvidenceNavigator.NavigationStatus.UNAVAILABLE, "Evidence is unavailable.");
            }
            if (!result.opened()) button.setEnabled(false);
        });
        return button;
    }

    private static String label(EvidenceCoverageView.Hunk hunk) {
        String symbol = switch (hunk.state()) {
            case "REFERENCED" -> "✓";
            case "UNREFERENCED" -> "○";
            default -> "?";
        };
        String status = switch (hunk.state()) {
            case "REFERENCED" -> hunk.categoryIds().stream().map(EvidenceCoveragePanel::display)
                    .reduce((left, right) -> left + ", " + right).orElse("Referenced");
            case "UNREFERENCED" -> "Not referenced";
            default -> "Unmeasurable";
        };
        return symbol + " " + hunk.relativePath() + " · hunk " + hunk.ordinal()
                + " · lines " + hunk.startLine() + "–" + hunk.endLine() + " · " + status;
    }

    private static String display(String value) {
        String normalized = value.replace('-', ' ');
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private static int order(String state) {
        return switch (state) { case "UNREFERENCED" -> 0; case "REFERENCED" -> 1; default -> 2; };
    }
}
