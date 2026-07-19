package dev.codedefense.application;

import dev.codedefense.analysis.ProjectAnalyzer;
import dev.codedefense.analysis.StagedChangeAnalyzer;
import dev.codedefense.interview.InterviewRunner;
import dev.codedefense.report.ReportService;
import java.util.Objects;
import java.util.function.Supplier;

public record CodeDefenseRuntime(ProjectAnalyzer analyzer, Supplier<StagedChangeAnalyzer> stagedChangeAnalyzerFactory,
        InterviewRunner interviewRunner, ReportService reportService) {
    public CodeDefenseRuntime {
        Objects.requireNonNull(analyzer);
        Objects.requireNonNull(stagedChangeAnalyzerFactory);
        Objects.requireNonNull(interviewRunner);
        Objects.requireNonNull(reportService);
    }

    public CodeDefenseRuntime(ProjectAnalyzer analyzer, InterviewRunner interviewRunner, ReportService reportService) {
        this(analyzer, () -> (change, snapshot) -> {
            throw new UnsupportedOperationException("Staged change analysis is not configured.");
        }, interviewRunner, reportService);
    }

    public StagedChangeAnalyzer stagedChangeAnalyzer() {
        return stagedChangeAnalyzerFactory.get();
    }
}
