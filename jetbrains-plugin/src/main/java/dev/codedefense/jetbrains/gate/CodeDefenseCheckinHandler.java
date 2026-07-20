package dev.codedefense.jetbrains.gate;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import dev.codedefense.jetbrains.commit.PassportCommitTrailer;
import dev.codedefense.jetbrains.commit.PassportTrailerCommitOption;
import dev.codedefense.jetbrains.commit.PassportTrailerResult;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher.Selector;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComboBox;

/** Advisory pre-commit check for the exact staged index Passport. */
public final class CodeDefenseCheckinHandler extends CheckinHandler {
    private static final Duration FRESH_TIMEOUT = Duration.ofSeconds(15);
    private static final StagedGateView UNAVAILABLE = new StagedGateView(1,
            StagedGateView.State.UNAVAILABLE, StagedGateView.Reason.GIT_CAPTURE_FAILED,
            "", 0, 0, 0, 0, java.util.List.of());

    private final CommitModeDetector modeDetector;
    private final FreshGate gate;
    private final CommitGatePrompt prompt;
    private final ProgressRunner progress;
    private final Runnable defend;
    private final PassportTrailerCommitOption trailerOption;
    private final CommitMessage commitMessage;
    private final TrailerNotice trailerNotice;
    private final PassportCommitTrailer trailer = new PassportCommitTrailer();

    CodeDefenseCheckinHandler(CommitModeDetector modeDetector, FreshGate gate,
            CommitGatePrompt prompt, ProgressRunner progress, Runnable defend) {
        this(modeDetector, gate, prompt, progress, defend,
                new PassportTrailerCommitOption(gate::fresh, Runnable::run, Runnable::run),
                CommitMessage.noop(), TrailerNotice.noop());
    }

    CodeDefenseCheckinHandler(CommitModeDetector modeDetector, FreshGate gate,
            CommitGatePrompt prompt, ProgressRunner progress, Runnable defend,
            PassportTrailerCommitOption trailerOption, CommitMessage commitMessage,
            TrailerNotice trailerNotice) {
        this.modeDetector = Objects.requireNonNull(modeDetector, "modeDetector");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.defend = Objects.requireNonNull(defend, "defend");
        this.trailerOption = Objects.requireNonNull(trailerOption, "trailerOption");
        this.commitMessage = Objects.requireNonNull(commitMessage, "commitMessage");
        this.trailerNotice = Objects.requireNonNull(trailerNotice, "trailerNotice");
    }

    static CodeDefenseCheckinHandler forProject(Project project,
            com.intellij.openapi.vcs.CheckinProjectPanel panel, CommitModeDetector modeDetector) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(panel, "panel");
        CodeDefenseProjectGateService service = CodeDefenseProjectGateService.getInstance(project);
        ProgressRunner runner = operation -> ProgressManager.getInstance()
                .runProcessWithProgressSynchronously(
                        (ThrowableComputable<StagedGateView, RuntimeException>) () -> {
                            StagedGateView result = operation.get();
                            ProgressManager.checkCanceled();
                            return result;
                        },
                        "Checking CodeDefense Passport", true, project);
        PassportTrailerCommitOption option = PassportTrailerCommitOption.production(service::fresh);
        CommitMessage message = new CommitMessage() {
            @Override public String get() { return panel.getCommitMessage(); }
            @Override public void set(String value) { panel.setCommitMessage(value); }
        };
        TrailerNotice notice = new TrailerNotice() {
            @Override public void conflict(String text) {
                Messages.showWarningDialog(project, text, "CodeDefense");
            }
            @Override public void stale(String text) {
                Messages.showWarningDialog(project, text, "CodeDefense");
            }
        };
        return new CodeDefenseCheckinHandler(modeDetector, service::fresh,
                CommitGatePrompt.dialogs(project), runner, () -> openStagedPreview(project),
                option, message, notice);
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        return trailerOption;
    }

    @Override
    public ReturnResult beforeCheckin() {
        if (modeDetector.detect() != CommitModeDetector.Mode.STAGED_ONLY) {
            return prompt.askUnsupportedCommitMode() == CommitGatePrompt.Decision.COMMIT_ANYWAY
                    ? ReturnResult.COMMIT : ReturnResult.CANCEL;
        }

        StagedGateView state;
        try {
            state = Objects.requireNonNull(progress.run(() -> gate.fresh(FRESH_TIMEOUT)), "state");
        } catch (ProcessCanceledException exception) {
            return ReturnResult.CANCEL;
        } catch (RuntimeException exception) {
            state = UNAVAILABLE;
        }
        if (state.state() == StagedGateView.State.CURRENT) {
            return trailerOption.isSelected() ? attachTrailer(state) : ReturnResult.COMMIT;
        }

        return switch (prompt.ask(state)) {
            case COMMIT_ANYWAY -> ReturnResult.COMMIT;
            case DEFEND -> {
                defend.run();
                yield ReturnResult.CANCEL;
            }
            case CANCEL -> ReturnResult.CANCEL;
        };
    }

    private ReturnResult attachTrailer(StagedGateView first) {
        String original = commitMessage.get();
        PassportTrailerResult candidate = trailer.apply(original, first.diffFingerprint());
        if (candidate.status() == PassportTrailerResult.Status.CONFLICT) {
            trailerNotice.conflict("A conflicting CodeDefense Passport trailer already exists. "
                    + "Remove it or leave Passport attachment unchecked.");
            return ReturnResult.CANCEL;
        }

        StagedGateView second;
        try {
            second = Objects.requireNonNull(progress.run(() -> gate.fresh(FRESH_TIMEOUT)), "state");
        } catch (ProcessCanceledException exception) {
            return ReturnResult.CANCEL;
        } catch (RuntimeException exception) {
            second = UNAVAILABLE;
        }
        if (second.state() != StagedGateView.State.CURRENT
                || !second.diffFingerprint().equals(first.diffFingerprint())
                || !commitMessage.get().equals(original)) {
            trailerNotice.stale("The staged change or commit message changed while attaching its Passport. "
                    + "Refresh the defense and try again.");
            return ReturnResult.CANCEL;
        }
        if (candidate.status() == PassportTrailerResult.Status.ADDED) {
            commitMessage.set(candidate.commitMessage());
        }
        return ReturnResult.COMMIT;
    }

    private static void openStagedPreview(Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeDefense");
        if (toolWindow == null) return;
        toolWindow.activate(() -> prepareStagedPreview(toolWindow), true);
    }

    private static void prepareStagedPreview(ToolWindow toolWindow) {
        Content[] contents = toolWindow.getContentManager().getContents();
        if (contents.length == 0) return;
        Component selector = find(contents[0].getComponent(), "codeDefense.changeSelector");
        Component preview = find(contents[0].getComponent(), "codeDefense.previewDefense");
        if (selector instanceof JComboBox<?> comboBox) comboBox.setSelectedItem(Selector.STAGED);
        if (preview instanceof JButton button) {
            button.setEnabled(true);
            button.requestFocusInWindow();
        }
    }

    private static Component find(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @FunctionalInterface
    interface FreshGate {
        StagedGateView fresh(Duration timeout);
    }

    @FunctionalInterface
    interface ProgressRunner {
        StagedGateView run(Supplier<StagedGateView> operation);
    }

    interface CommitMessage {
        String get();
        void set(String value);

        static CommitMessage noop() {
            return new CommitMessage() {
                @Override public String get() { return ""; }
                @Override public void set(String value) { }
            };
        }
    }

    interface TrailerNotice {
        void conflict(String message);
        void stale(String message);

        static TrailerNotice noop() {
            return new TrailerNotice() {
                @Override public void conflict(String message) { }
                @Override public void stale(String message) { }
            };
        }
    }
}
