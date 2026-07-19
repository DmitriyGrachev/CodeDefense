package dev.codedefense.jetbrains.gate;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import java.util.Objects;

/** Creates the project-scoped advisory CodeDefense commit handler. */
public final class CodeDefenseCheckinHandlerFactory extends CheckinHandlerFactory {
    @Override
    public CheckinHandler createHandler(CheckinProjectPanel panel, CommitContext context) {
        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(context, "context");
        return CodeDefenseCheckinHandler.forProject(panel.getProject(),
                CommitModeDetector.forCommit(panel, context));
    }
}
