package dev.codedefense.jetbrains.gate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Objects;

/** Advisory user decision port; implementations must not persist the decision. */
public interface CommitGatePrompt {
    String MISSING_PASSPORT = "This staged change does not have a current CodeDefense Passport.";

    Decision ask(StagedGateView state);

    /** Unsupported changelist commits intentionally have no Defend action. */
    default Decision askUnsupportedCommitMode() {
        return Decision.CANCEL;
    }

    static CommitGatePrompt dialogs(Project project) {
        Objects.requireNonNull(project, "project");
        return new CommitGatePrompt() {
            @Override public Decision ask(StagedGateView state) {
                Objects.requireNonNull(state, "state");
                int answer = Messages.showDialog(project, MISSING_PASSPORT, "CodeDefense",
                        new String[] {"Defend change", "Commit anyway", "Cancel"}, 2,
                        Messages.getWarningIcon());
                return switch (answer) {
                    case 0 -> Decision.DEFEND;
                    case 1 -> Decision.COMMIT_ANYWAY;
                    default -> Decision.CANCEL;
                };
            }

            @Override public Decision askUnsupportedCommitMode() {
                int answer = Messages.showDialog(project,
                        "UNSUPPORTED COMMIT MODE: CodeDefense verifies only the exact staged index.",
                        "CodeDefense", new String[] {"Commit anyway", "Cancel"}, 1,
                        Messages.getWarningIcon());
                return answer == 0 ? Decision.COMMIT_ANYWAY : Decision.CANCEL;
            }
        };
    }

    enum Decision {
        DEFEND, COMMIT_ANYWAY, CANCEL
    }
}
