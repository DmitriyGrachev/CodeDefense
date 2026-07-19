package dev.codedefense.jetbrains.gate;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import git4idea.index.GitStageCommitWorkflowHandler;
import java.util.Objects;

/** Isolates public IntelliJ/Git4Idea commit-mode inspection from the gate policy. */
@FunctionalInterface
public interface CommitModeDetector {
    Mode detect();

    static CommitModeDetector forCommit(CheckinProjectPanel panel, CommitContext context) {
        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(context, "context");
        return () -> panel.getCommitWorkflowHandler() instanceof GitStageCommitWorkflowHandler
                ? Mode.STAGED_ONLY : Mode.UNSUPPORTED;
    }

    enum Mode {
        STAGED_ONLY, UNSUPPORTED
    }
}
