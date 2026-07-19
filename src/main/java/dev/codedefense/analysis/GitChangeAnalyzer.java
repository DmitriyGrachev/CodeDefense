package dev.codedefense.analysis;

import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.DefenseFocus;

public interface GitChangeAnalyzer {
    ProjectAnalysis analyze(GitChange change, ProjectSnapshot snapshot);
    default ProjectAnalysis analyze(GitChange change, ProjectSnapshot snapshot, DefenseFocus focus) {
        return analyze(change, snapshot);
    }
}
