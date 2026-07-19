package dev.codedefense.domain;

import java.time.Instant;
import java.util.Objects;

public record ChangePassport(
        StagedChange change,
        ProjectAnalysis analysis,
        InterviewSession session,
        Instant createdAt,
        String model,
        PassportStatus statusAtCreation) {
    public ChangePassport {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(createdAt, "createdAt");
        model = CodeEvidence.requireNonBlank(model, "model");
        Objects.requireNonNull(statusAtCreation, "statusAtCreation");
        if (analysis.questions().size() != 3) {
            throw new IllegalArgumentException("analysis requires exactly three questions");
        }
        if (!analysis.projectName().equals(session.projectName())) {
            throw new IllegalArgumentException("session must belong to the analysis project");
        }
        for (int index = 0; index < analysis.questions().size(); index++) {
            if (!analysis.questions().get(index).id().equals(session.results().get(index).question().id())) {
                throw new IllegalArgumentException("session results must match analysis questions in order");
            }
        }
    }

    @Override
    public String toString() {
        return "ChangePassport[projectName=%s, fileCount=%d, createdAt=%s, model=%s, statusAtCreation=%s]"
                .formatted(analysis.projectName(), change.files().size(), createdAt, model, statusAtCreation);
    }
}
