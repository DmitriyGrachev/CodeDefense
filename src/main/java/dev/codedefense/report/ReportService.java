package dev.codedefense.report;

import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.SavedReport;

/** Application boundary for producing and storing an understanding report. */
public interface ReportService {
    SavedReport generateAndSave(ProjectSnapshot snapshot, ProjectAnalysis analysis, InterviewSession session);
}
