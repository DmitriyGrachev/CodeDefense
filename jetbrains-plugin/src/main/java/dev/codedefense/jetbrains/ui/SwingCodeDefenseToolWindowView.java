package dev.codedefense.jetbrains.ui;

import dev.codedefense.jetbrains.process.CodeDefenseLauncher.Selector;
import dev.codedefense.jetbrains.gate.StagedGateView;
import dev.codedefense.jetbrains.evidence.EvidenceNavigator;
import dev.codedefense.jetbrains.process.EvidenceLocationView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import javax.swing.KeyStroke;
import javax.swing.BoxLayout;

public final class SwingCodeDefenseToolWindowView implements CodeDefenseToolWindowView {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JComboBox<Selector> selector = new JComboBox<>(Selector.values());
    private final JTextField selectorValue = new JTextField(12);
    private final JComboBox<String> focus = new JComboBox<>(new String[] {
            "balanced", "architecture", "failure-modes", "testing"});
    private final JButton preview = new JButton("Preview defense");
    private final JButton start = new JButton("Start defense");
    private final JButton cancel = new JButton("Cancel");
    private final JButton refresh = new JButton("Refresh");
    private final JButton openPassport = new JButton("Open Passport");
    private final JButton defendStaged = new JButton("Defend staged change");
    private final JLabel gateBadge = new JLabel("UNAVAILABLE");
    private final JLabel gateSummary = new JLabel("Status unavailable");
    private final JButton accept = new JButton("Send bounded source");
    private final JButton decline = new JButton("Decline");
    private final JTextArea output = new JTextArea();
    private final JPanel evidencePanel = new JPanel();
    private final JTextField answer = new JTextField();
    private final JButton submit = new JButton("Answer");
    private final JButton skip = new JButton("Skip");
    private final JCheckBox provenance = new JCheckBox("Experimental Codex provenance");
    private final JTextField threadId = new JTextField(18);
    private final JCheckBox historyConsent = new JCheckBox("Read this selected local thread for this run only");
    private String passportPath;

    public SwingCodeDefenseToolWindowView() {
        selector.setName("codeDefense.changeSelector");
        preview.setName("codeDefense.previewDefense");
        gateBadge.setName("codeDefense.gateBadge");
        gateSummary.setName("codeDefense.gateSummary");
        evidencePanel.setName("codeDefense.evidencePanel");
        evidencePanel.setLayout(new BoxLayout(evidencePanel, BoxLayout.Y_AXIS));
        evidencePanel.setVisible(false);
        JPanel gate = new JPanel(new FlowLayout(FlowLayout.LEFT));
        gate.add(new JLabel("Staged Passport:")); gate.add(gateBadge); gate.add(gateSummary);
        JPanel selectors = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectors.add(new JLabel("Change:")); selectors.add(selector); selectors.add(selectorValue);
        selectors.add(new JLabel("Focus:")); selectors.add(focus);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(preview); actions.add(start); actions.add(cancel); actions.add(refresh);
        actions.add(defendStaged); actions.add(openPassport);
        JPanel confirmation = new JPanel(new FlowLayout(FlowLayout.LEFT));
        confirmation.add(accept); confirmation.add(decline);
        JPanel input = new JPanel(new BorderLayout(4, 4));
        input.add(answer, BorderLayout.CENTER);
        JPanel inputButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        inputButtons.add(submit); inputButtons.add(skip); input.add(inputButtons, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.add(confirmation, BorderLayout.NORTH); south.add(input, BorderLayout.SOUTH);
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        JPanel provenanceControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        provenanceControls.add(provenance); provenanceControls.add(new JLabel("Thread ID:"));
        provenanceControls.add(threadId); provenanceControls.add(historyConsent);
        provenanceControls.setVisible("true".equalsIgnoreCase(
                System.getenv("CODEDEFENSE_EXPERIMENTAL_CODEX_PROVENANCE")));
        JPanel north = new JPanel(); north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(gate); north.add(selectors); north.add(actions); north.add(provenanceControls);
        root.add(north, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JScrollPane(output), BorderLayout.CENTER);
        center.add(evidencePanel, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setSessionActive(false);
    }

    public void bind(CodeDefenseToolWindowController controller) {
        preview.addActionListener(event -> invoke(() -> {
            if (provenance.isSelected()) controller.preview(selected(), selectorArgument(), selectedFocus(),
                    threadId.getText(), historyConsent.isSelected());
            else controller.preview(selected(), selectorArgument(), selectedFocus());
        }));
        start.addActionListener(event -> invoke(() -> {
            if (provenance.isSelected()) controller.start(selected(), selectorArgument(), selectedFocus(),
                    threadId.getText(), historyConsent.isSelected());
            else controller.start(selected(), selectorArgument(), selectedFocus());
        }));
        cancel.addActionListener(event -> controller.cancel());
        accept.addActionListener(event -> confirm(controller, true));
        decline.addActionListener(event -> confirm(controller, false));
        submit.addActionListener(event -> controller.answer(answer.getText()));
        skip.addActionListener(event -> controller.skip());
        refresh.addActionListener(event -> controller.refresh());
        defendStaged.addActionListener(event -> controller.defendStagedChange());
        openPassport.addActionListener(event -> controller.openPassport());
    }

    public JComponent component() { return root; }
    public String passportPath() { return passportPath; }
    public void configureDefaults(Selector defaultSelector, String defaultFocus) {
        selector.setSelectedItem(defaultSelector);
        focus.setSelectedItem(defaultFocus);
    }

    private Selector selected() { return (Selector) selector.getSelectedItem(); }
    private String selectedFocus() { return (String) focus.getSelectedItem(); }
    private String selectorArgument() {
        return selected() == Selector.STAGED ? null : selectorValue.getText().trim();
    }

    @Override public void setSessionActive(boolean active) {
        preview.setEnabled(!active); start.setEnabled(!active); cancel.setEnabled(active);
        setConfirmationEnabled(false); answer.setEnabled(active);
        submit.setEnabled(active); skip.setEnabled(active);
    }
    @Override public void setConfirmationEnabled(boolean enabled) {
        accept.setEnabled(enabled); decline.setEnabled(enabled);
    }
    @Override public void showPreview(String value) { append("Preview: " + value); }
    @Override public void showConfirmation(String value) { append(value); }
    @Override public void showQuestion(String value) { append(value); }
    @Override public void showEvaluation(String value) { append(value); }
    @Override public void showQuestionScore(String value) { append(value); }
    @Override public void showSummary(String value) { append(value); }
    @Override public void showPassportSaved(String path, String value) { passportPath = path; append(value); }
    @Override public void showCompleted(String value) { append(value); }
    @Override public void showError(String value) { append("Error: " + value); }
    @Override public void clearAnswer() { answer.setText(""); }
    @Override public void showPassportStatus(String value) { append("Passport: " + value); }
    @Override public void showProvenance(String value) { append("Experimental Codex provenance: " + value); }
    @Override public void clearProvenanceConsent() {
        threadId.setText(""); historyConsent.setSelected(false); provenance.setSelected(false);
    }
    @Override public void showGateStatus(StagedGateView value) {
        String state = value.state().name().replace('_', ' ');
        gateBadge.setText(state);
        gateBadge.setForeground(color(value.state()));
        String summary = switch (value.state()) {
            case NO_STAGED_CHANGE -> "No staged files";
            case UNAVAILABLE -> "Status unavailable";
            case CURRENT -> counts(value) + " | " + value.shortFingerprint();
            case UNDEFENDED, EXPIRED -> counts(value);
        };
        gateSummary.setText(summary);
        gateBadge.getAccessibleContext().setAccessibleName(
                "CodeDefense staged gate: " + state + ". " + summary);
        gateSummary.getAccessibleContext().setAccessibleName(summary);
    }
    @Override public void prepareStagedDefense() {
        selector.setSelectedItem(Selector.STAGED);
        preview.setEnabled(true);
        preview.requestFocusInWindow();
    }
    @Override public void showEvidence(List<EvidenceLocationView> locations,
            Function<EvidenceLocationView, EvidenceNavigator.NavigationResult> opener) {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(opener, "opener");
        clearEvidence();
        for (int index = 0; index < locations.size(); index++) {
            EvidenceLocationView location = Objects.requireNonNull(locations.get(index), "location");
            JButton item = new JButton(evidenceLabel(location));
            item.setName("codeDefense.evidence." + index);
            item.setHorizontalAlignment(JButton.LEFT);
            item.getAccessibleContext().setAccessibleName("Open evidence " + location.relativePath()
                    + " lines " + location.startLine() + " to " + location.endLine());
            item.addActionListener(event -> openEvidence(item, location, opener));
            item.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openCodeDefenseEvidence");
            item.getActionMap().put("openCodeDefenseEvidence", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { item.doClick(); }
            });
            evidencePanel.add(item);
        }
        evidencePanel.setVisible(!locations.isEmpty());
        evidencePanel.revalidate();
        evidencePanel.repaint();
    }
    @Override public void clearEvidence() {
        evidencePanel.removeAll();
        evidencePanel.setVisible(false);
        evidencePanel.revalidate();
        evidencePanel.repaint();
    }

    private String counts(StagedGateView value) {
        return value.stagedFileCount() + " staged files | +" + value.addedLines()
                + "/-" + value.deletedLines();
    }

    private String evidenceLabel(EvidenceLocationView location) {
        if (location.startLine() == location.endLine()) {
            return location.relativePath() + ":" + location.startLine();
        }
        return location.relativePath() + ":" + location.startLine() + "\u2013" + location.endLine();
    }

    private void openEvidence(JButton item, EvidenceLocationView location,
            Function<EvidenceLocationView, EvidenceNavigator.NavigationResult> opener) {
        EvidenceNavigator.NavigationResult result;
        try {
            result = Objects.requireNonNull(opener.apply(location), "navigationResult");
        } catch (RuntimeException exception) {
            result = new EvidenceNavigator.NavigationResult(
                    EvidenceNavigator.NavigationStatus.UNAVAILABLE, "Evidence file is unavailable.");
        }
        if (!result.opened()) {
            item.setEnabled(false);
            append(navigationMessage(result.status()));
        }
    }

    private String navigationMessage(EvidenceNavigator.NavigationStatus status) {
        return switch (status) {
            case OPENED -> "Evidence opened.";
            case UNSAFE -> "Unsafe evidence was not opened.";
            case UNAVAILABLE -> "Evidence file is unavailable.";
            case STALE -> "Evidence location is stale.";
        };
    }

    private Color color(StagedGateView.State state) {
        return switch (state) {
            case NO_STAGED_CHANGE -> Color.GRAY;
            case UNDEFENDED -> new Color(190, 110, 0);
            case CURRENT -> new Color(0, 128, 0);
            case EXPIRED -> new Color(190, 0, 0);
            case UNAVAILABLE -> new Color(170, 140, 0);
        };
    }

    private void append(String value) {
        if (!output.getText().isEmpty()) output.append("\n\n");
        output.append(value);
        output.setCaretPosition(output.getDocument().getLength());
    }

    private void invoke(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException exception) { showError(exception.getMessage()); }
    }

    private void confirm(CodeDefenseToolWindowController controller, boolean accepted) {
        setConfirmationEnabled(false);
        controller.confirm(accepted);
    }
}
