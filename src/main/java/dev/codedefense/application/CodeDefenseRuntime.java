package dev.codedefense.application;
import dev.codedefense.analysis.ProjectAnalyzer; import dev.codedefense.interview.InterviewRunner; import java.util.Objects;
public record CodeDefenseRuntime(ProjectAnalyzer analyzer, InterviewRunner interviewRunner) { public CodeDefenseRuntime { Objects.requireNonNull(analyzer);Objects.requireNonNull(interviewRunner); } }
