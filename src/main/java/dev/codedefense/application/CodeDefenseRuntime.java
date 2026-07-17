package dev.codedefense.application;

import dev.codedefense.analysis.ProjectAnalyzer;
import dev.codedefense.interview.InterviewRunner;
import dev.codedefense.report.ReportService;
import java.util.Objects;

public record CodeDefenseRuntime(ProjectAnalyzer analyzer, InterviewRunner interviewRunner, ReportService reportService) {
    public CodeDefenseRuntime {
        Objects.requireNonNull(analyzer);
        Objects.requireNonNull(interviewRunner);
        Objects.requireNonNull(reportService);
    }
}
