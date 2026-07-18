package dev.codedefense.analysis;

import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;

public interface StagedChangeAnalyzer {
    ProjectAnalysis analyze(StagedChange change, ProjectSnapshot snapshot);
}
