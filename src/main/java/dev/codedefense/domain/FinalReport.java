package dev.codedefense.domain;

import java.util.Objects;

public record FinalReport(ReportMetadata metadata, ProjectAnalysis analysis, InterviewSession session,
                          ReportNarrative narrative, NarrativeSource narrativeSource) {
    public FinalReport {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(narrative, "narrative");
        Objects.requireNonNull(narrativeSource, "narrativeSource");
        new ReportGenerationRequest(analysis, session);
        if (!metadata.projectName().equals(analysis.projectName()) || !metadata.projectType().equals(analysis.projectType())) {
            throw new IllegalArgumentException("Report metadata must match project analysis identity");
        }
    }

    @Override
    public String toString() {
        return "FinalReport[projectName=%s, projectType=%s, questionCount=%d, narrativeSource=%s]"
                .formatted(metadata.projectName(), metadata.projectType(), analysis.questions().size(), narrativeSource);
    }
}
