package dev.codedefense.jetbrains.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CodeDefenseConfigurable implements SearchableConfigurable {
    private JPanel panel;
    private JRadioButton bundled;
    private JRadioButton override;
    private JTextField overridePath;
    private JComboBox<String> selector;
    private JComboBox<String> focus;

    @Override public @NotNull String getId() { return "dev.codedefense.jetbrains.settings"; }
    @Override public @Nls String getDisplayName() { return "CodeDefense"; }

    @Override public @Nullable JComponent createComponent() {
        panel = new JPanel(new GridBagLayout());
        bundled = new JRadioButton("Use bundled CodeDefense CLI");
        override = new JRadioButton("Use explicit CodeDefense JAR");
        ButtonGroup group = new ButtonGroup(); group.add(bundled); group.add(override);
        overridePath = new JTextField(32);
        selector = new JComboBox<>(new String[] {"STAGED", "COMMIT", "RANGE"});
        focus = new JComboBox<>(new String[] {"balanced", "architecture", "failure-modes", "testing"});
        int row = 0;
        add(bundled, row++); add(override, row++); add(overridePath, row++);
        add(new JLabel("Default selector"), row++); add(selector, row++);
        add(new JLabel("Default focus"), row++); add(focus, row);
        reset();
        return panel;
    }

    private void add(JComponent component, int row) {
        panel.add(component, new GridBagConstraints(0, row, 1, 1, 1, 0,
                GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                new Insets(4, 4, 4, 4), 0, 0));
    }

    @Override public boolean isModified() {
        var state = CodeDefenseSettings.getInstance().getState();
        return bundled.isSelected() != state.useBundledCli
                || !overridePath.getText().trim().equals(state.cliJarOverride)
                || !selector.getSelectedItem().equals(state.defaultSelector)
                || !focus.getSelectedItem().equals(state.defaultFocus);
    }

    @Override public void apply() throws ConfigurationException {
        try {
            CodeDefenseSettings.getInstance().update(bundled.isSelected(), overridePath.getText(),
                    (String) selector.getSelectedItem(), (String) focus.getSelectedItem());
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationException(exception.getMessage());
        }
    }

    @Override public void reset() {
        if (bundled == null) return;
        var state = CodeDefenseSettings.getInstance().getState();
        bundled.setSelected(state.useBundledCli); override.setSelected(!state.useBundledCli);
        overridePath.setText(state.cliJarOverride); selector.setSelectedItem(state.defaultSelector);
        focus.setSelectedItem(state.defaultFocus);
    }

    @Override public void disposeUIResources() {
        panel = null; bundled = null; override = null; overridePath = null; selector = null; focus = null;
    }
}
