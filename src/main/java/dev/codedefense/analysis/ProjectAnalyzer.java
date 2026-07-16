package dev.codedefense.analysis;

import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;

public interface ProjectAnalyzer {
    ProjectAnalysis analyze(ProjectSnapshot snapshot);
}
