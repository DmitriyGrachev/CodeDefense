package dev.codedefense.analysis;

import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.DefenseFocus;

public interface StagedChangeAnalyzer {
    ProjectAnalysis analyze(StagedChange change, ProjectSnapshot snapshot);
    default ProjectAnalysis analyze(StagedChange change, ProjectSnapshot snapshot, DefenseFocus focus) {
        return analyze(change, snapshot);
    }
}
