package dev.codedefense.change;

import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.ProjectSnapshot;
import java.nio.charset.StandardCharsets;

public final class GitChangeContextBuilder {
    private final StagedChangeContextBuilder delegate = new StagedChangeContextBuilder();

    public ProjectSnapshot build(CapturedGitChange captured) {
        GitChange change = captured.change();
        var staged = GitChangeAdapters.asStagedChange(change);
        ProjectSnapshot snapshot = delegate.build(new CapturedStagedChange(staged, captured.hunks()));
        String label = switch (change.kind()) {
            case STAGED -> "Staged Git change";
            case COMMIT -> "Git commit";
            case RANGE -> "Git range";
        };
        String prompt = snapshot.promptContent()
                .replaceFirst("STAGED_CHANGE", "GIT_CHANGE")
                .replaceFirst("indexIdentity:", "targetIdentity:");
        return new ProjectSnapshot(snapshot.root(), snapshot.projectName(), label, snapshot.scanSummary(),
                snapshot.selectedFiles(), prompt, prompt.getBytes(StandardCharsets.UTF_8).length,
                snapshot.redactionCount());
    }

}
