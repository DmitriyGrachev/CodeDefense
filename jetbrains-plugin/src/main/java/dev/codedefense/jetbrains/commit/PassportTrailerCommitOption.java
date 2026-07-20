package dev.codedefense.jetbrains.commit;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import dev.codedefense.jetbrains.gate.StagedGateView;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JCheckBox;
import javax.swing.JComponent;

/** Per-handler, non-persistent consent to add a source-free Passport trailer. */
public final class PassportTrailerCommitOption implements RefreshableOnComponent {
    private static final Duration PREFLIGHT_TIMEOUT = Duration.ofSeconds(15);
    private static final String LABEL = "Attach current CodeDefense Passport fingerprint";

    private final Gate gate;
    private final TaskExecutor background;
    private final TaskExecutor ui;
    private final JCheckBox checkBox = new JCheckBox(LABEL);
    private final AtomicBoolean opened = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();

    public PassportTrailerCommitOption(Gate gate, TaskExecutor background, TaskExecutor ui) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        checkBox.setSelected(false);
        checkBox.setEnabled(false);
        checkBox.getAccessibleContext().setAccessibleName(LABEL);
        checkBox.getAccessibleContext().setAccessibleDescription(
                "Adds only the current source-free CodeDefense Passport fingerprint.");
    }

    public static PassportTrailerCommitOption production(Gate gate) {
        var application = ApplicationManager.getApplication();
        return new PassportTrailerCommitOption(gate,
                task -> application.executeOnPooledThread(task),
                task -> application.invokeLater(task));
    }

    @Override
    public JComponent getComponent() {
        if (opened.compareAndSet(false, true)) startPreflight();
        return checkBox;
    }

    @Override
    public void refresh() {
        startPreflight();
    }

    @Override
    public void saveState() {
        // Explicitly per commit UI instance; never persisted.
    }

    @Override
    public void restoreState() {
        checkBox.setSelected(false);
    }

    public boolean isSelected() {
        return checkBox.isEnabled() && checkBox.isSelected();
    }

    public boolean isEnabled() {
        return checkBox.isEnabled();
    }

    private void startPreflight() {
        long requested = generation.incrementAndGet();
        checkBox.setSelected(false);
        checkBox.setEnabled(false);
        background.execute(() -> {
            boolean current;
            try {
                StagedGateView result = Objects.requireNonNull(gate.fresh(PREFLIGHT_TIMEOUT), "result");
                current = result.state() == StagedGateView.State.CURRENT;
            } catch (RuntimeException exception) {
                current = false;
            }
            boolean enable = current;
            ui.execute(() -> {
                if (generation.get() != requested) return;
                checkBox.setSelected(false);
                checkBox.setEnabled(enable);
            });
        });
    }

    @FunctionalInterface
    public interface Gate {
        StagedGateView fresh(Duration timeout);
    }

    @FunctionalInterface
    public interface TaskExecutor {
        void execute(Runnable task);
    }
}
