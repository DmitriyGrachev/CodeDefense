package dev.codedefense.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ChangePassport(
        StagedChange change,
        ChangeKind changeKind,
        String sourceIdentity,
        ProjectAnalysis analysis,
        InterviewSession session,
        Instant createdAt,
        String model,
        PassportStatus statusAtCreation,
        DefenseFocus focus,
        Optional<CodexProvenanceSummary> codexProvenance,
        Optional<EvidenceCoverageMap> evidenceCoverage) {
    public ChangePassport {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(changeKind, "changeKind");
        sourceIdentity = CodeEvidence.requireNonBlank(sourceIdentity, "sourceIdentity");
        if (changeKind == ChangeKind.STAGED ? !sourceIdentity.matches("[0-9a-f]{64}")
                : !sourceIdentity.matches("[0-9a-f]{40,64}")) {
            throw new IllegalArgumentException("sourceIdentity has an invalid format");
        }
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(createdAt, "createdAt");
        model = CodeEvidence.requireNonBlank(model, "model");
        Objects.requireNonNull(statusAtCreation, "statusAtCreation");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(codexProvenance, "codexProvenance");
        Objects.requireNonNull(evidenceCoverage, "evidenceCoverage");
        evidenceCoverage.ifPresent(value -> {
            if (!value.diffFingerprint().equals(change.diffFingerprint())) {
                throw new IllegalArgumentException("coverage must belong to the Passport change");
            }
        });
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

    public ChangePassport(StagedChange change, ProjectAnalysis analysis, InterviewSession session,
            Instant createdAt, String model, PassportStatus statusAtCreation) {
        this(change, ChangeKind.STAGED, change.indexIdentity(), analysis, session, createdAt, model,
                statusAtCreation, DefenseFocus.BALANCED, Optional.empty(), Optional.empty());
    }

    public ChangePassport(StagedChange change, ChangeKind changeKind, String sourceIdentity,
            ProjectAnalysis analysis, InterviewSession session, Instant createdAt, String model,
            PassportStatus statusAtCreation) {
        this(change, changeKind, sourceIdentity, analysis, session, createdAt, model, statusAtCreation,
                DefenseFocus.BALANCED, Optional.empty(), Optional.empty());
    }

    public ChangePassport(StagedChange change, ChangeKind changeKind, String sourceIdentity,
            ProjectAnalysis analysis, InterviewSession session, Instant createdAt, String model,
            PassportStatus statusAtCreation, DefenseFocus focus) {
        this(change, changeKind, sourceIdentity, analysis, session, createdAt, model,
                statusAtCreation, focus, Optional.empty(), Optional.empty());
    }

    public ChangePassport(StagedChange change, ChangeKind changeKind, String sourceIdentity,
            ProjectAnalysis analysis, InterviewSession session, Instant createdAt, String model,
            PassportStatus statusAtCreation, DefenseFocus focus,
            Optional<CodexProvenanceSummary> codexProvenance) {
        this(change, changeKind, sourceIdentity, analysis, session, createdAt, model,
                statusAtCreation, focus, codexProvenance, Optional.empty());
    }

    @Override
    public String toString() {
        return "ChangePassport[projectName=%s, fileCount=%d, createdAt=%s, model=%s, statusAtCreation=%s]"
                .formatted(analysis.projectName(), change.files().size(), createdAt, model, statusAtCreation);
    }
}
