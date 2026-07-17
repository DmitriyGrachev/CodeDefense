package dev.codedefense.report;

import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.domain.FinalReport;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.NarrativeSource;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ReportGenerationRequest;
import dev.codedefense.domain.ReportMetadata;
import dev.codedefense.domain.ReportNarrative;
import dev.codedefense.domain.SavedReport;
import java.time.Clock;
import java.util.Objects;

/** Generates exactly one optional AI narrative before persisting a final report. */
public final class UnderstandingReportService implements ReportService {
    private final ReportNarrativeGenerator narrativeGenerator;
    private final DeterministicReportNarrativeFactory fallbackFactory;
    private final ReportStore reportStore;
    private final Clock clock;
    private final String model;

    public UnderstandingReportService(
            ReportNarrativeGenerator narrativeGenerator,
            DeterministicReportNarrativeFactory fallbackFactory,
            ReportStore reportStore,
            Clock clock,
            String model) {
        this.narrativeGenerator = Objects.requireNonNull(narrativeGenerator, "narrativeGenerator");
        this.fallbackFactory = Objects.requireNonNull(fallbackFactory, "fallbackFactory");
        this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must be nonblank");
        }
        this.model = model.strip();
    }

    @Override
    public SavedReport generateAndSave(ProjectSnapshot snapshot, ProjectAnalysis analysis, InterviewSession session) {
        Objects.requireNonNull(snapshot, "snapshot");
        ReportGenerationRequest request = new ReportGenerationRequest(analysis, session);
        ReportMetadata metadata = ReportMetadata.from(snapshot, model, clock.instant());
        ReportNarrative narrative;
        NarrativeSource source;
        try {
            narrative = narrativeGenerator.generate(request, metadata);
            source = NarrativeSource.AI;
        } catch (CodexNotInstalledException | CodexNotAuthenticatedException exception) {
            narrative = fallbackFactory.create(request);
            source = NarrativeSource.DETERMINISTIC_FALLBACK;
        }
        return reportStore.save(new FinalReport(metadata, analysis, session, narrative, source));
    }
}
