package dev.codedefense.analysis;

import dev.codedefense.change.GitChangeAdapters;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import java.util.Objects;
import dev.codedefense.domain.DefenseFocus;

/** Compatibility adapter keeping the validated analysis schema while generalizing Git selection. */
public final class AiGitChangeAnalyzer implements GitChangeAnalyzer {
    private final StagedChangeAnalyzer delegate;
    public AiGitChangeAnalyzer(StagedChangeAnalyzer delegate) { this.delegate = Objects.requireNonNull(delegate); }
    @Override public ProjectAnalysis analyze(GitChange change, ProjectSnapshot snapshot) {
        return delegate.analyze(GitChangeAdapters.asStagedChange(change), snapshot);
    }
    @Override public ProjectAnalysis analyze(GitChange change, ProjectSnapshot snapshot, DefenseFocus focus) {
        return delegate.analyze(GitChangeAdapters.asStagedChange(change), snapshot, focus);
    }
}
