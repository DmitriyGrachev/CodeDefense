package dev.codedefense.domain;

import java.util.Objects;

public record ReportGenerationRequest(ProjectAnalysis analysis, InterviewSession session) {
    public ReportGenerationRequest {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(session, "session");
        if (analysis.questions().size() != 3 || session.results().size() != 3) {
            throw new IllegalArgumentException("Report generation requires exactly three questions and results");
        }
        if (!analysis.projectName().equals(session.projectName())) {
            throw new IllegalArgumentException("Analysis and interview project names must match");
        }
        for (int index = 0; index < 3; index++) {
            if (!analysis.questions().get(index).id().equals(session.results().get(index).question().id())) {
                throw new IllegalArgumentException("Analysis questions and interview results must have matching IDs and order");
            }
        }
    }

    @Override
    public String toString() {
        return "ReportGenerationRequest[projectName=%s, questionCount=%d]".formatted(analysis.projectName(), analysis.questions().size());
    }
}
