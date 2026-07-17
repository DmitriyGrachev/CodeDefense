package dev.codedefense.domain;

import java.util.List;
import java.util.Objects;

public record InterviewSession(
        String projectName,
        List<QuestionResult> results,
        int overallScore,
        Readiness readiness,
        int skippedQuestionCount) {
    public InterviewSession {
        projectName = CodeEvidence.requireNonBlank(projectName, "projectName");
        Objects.requireNonNull(results, "results");
        results = List.copyOf(results);
        if (results.size() != 3 || results.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("An interview session requires exactly three results");
        }
        for (int index = 0; index < results.size(); index++) {
            if (results.get(index).questionNumber() != index + 1) {
                throw new IllegalArgumentException("Question results must be ordered one through three");
            }
        }
        if (overallScore < 0 || overallScore > 100) {
            throw new IllegalArgumentException("overallScore must be between 0 and 100");
        }
        Objects.requireNonNull(readiness, "readiness");
        long actualSkipped = results.stream()
                .filter(result -> result.primaryTurn().evaluation().verdict() == Verdict.SKIPPED)
                .count();
        if (skippedQuestionCount < 0 || skippedQuestionCount > 3 || skippedQuestionCount != actualSkipped) {
            throw new IllegalArgumentException("skippedQuestionCount is inconsistent with results");
        }
    }

    @Override
    public String toString() {
        return "InterviewSession[projectName=%s, resultCount=%d, overallScore=%d, readiness=%s, skippedQuestionCount=%d]"
                .formatted(projectName, results.size(), overallScore, readiness, skippedQuestionCount);
    }
}
