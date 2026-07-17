package dev.codedefense.report;

import dev.codedefense.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicReportNarrativeFactoryTest {
    @Test
    void derives_first_seen_concepts_caps_lists_and_adds_a_skipped_action_without_ai() {
        ReportGenerationRequest request = requestWith(
                evaluation(Verdict.PARTIAL, List.of(" Flow ", "Errors", "flow", "one", "two", "three"), List.of("Retry", "retry", "Logging", "alpha", "beta", "gamma")),
                evaluation(Verdict.SKIPPED, List.of(), List.of()),
                evaluation(Verdict.CORRECT, List.of("Unicode é"), List.of("Testing")));

        ReportNarrative narrative = new DeterministicReportNarrativeFactory().create(request);

        assertEquals(List.of("Flow", "Errors", "one", "two", "three", "Unicode é"), narrative.strengths());
        assertEquals(List.of("Retry", "Logging", "alpha", "beta", "gamma", "Testing"), narrative.knowledgeGaps());
        assertEquals(List.of("Review Retry.", "Review Logging.", "Review alpha.", "Review beta.", "Review gamma.", "Review Testing."), narrative.recommendedActions());
        assertTrue(narrative.summary().contains("1 skipped"));
    }

    @Test
    void adds_skipped_action_before_local_default_when_no_gaps_exist() {
        ReportNarrative narrative = new DeterministicReportNarrativeFactory().create(requestWith(
                evaluation(Verdict.SKIPPED, List.of(), List.of()),
                evaluation(Verdict.CORRECT, List.of("Flow"), List.of()),
                evaluation(Verdict.CORRECT, List.of(), List.of())));

        assertEquals(List.of("Return to question 1 and explain its design trade-offs."), narrative.recommendedActions());

        ReportNarrative noGaps = new DeterministicReportNarrativeFactory().create(requestWith(
                evaluation(Verdict.CORRECT, List.of("Flow"), List.of()),
                evaluation(Verdict.CORRECT, List.of(), List.of()),
                evaluation(Verdict.CORRECT, List.of(), List.of())));
        assertEquals(List.of("Review the project flow and explain the main design choices."), noGaps.recommendedActions());
    }

    private static AnswerEvaluation evaluation(Verdict verdict, List<String> understood, List<String> missing) {
        return new AnswerEvaluation(verdict, verdict == Verdict.SKIPPED ? 0 : 60, "Useful local feedback.", understood, missing, Optional.empty());
    }

    private static ReportGenerationRequest requestWith(AnswerEvaluation... evaluations) {
        ProjectAnalysis analysis = Fixtures.request().analysis();
        List<QuestionResult> results = java.util.stream.IntStream.range(0, 3).mapToObj(index -> {
            TechnicalQuestion question = analysis.questions().get(index);
            return new QuestionResult(index + 1, question,
                    new InterviewTurn(TurnType.PRIMARY, question.prompt(), "answer", evaluations[index]), Optional.empty(), evaluations[index].score());
        }).toList();
        return new ReportGenerationRequest(analysis, new InterviewSession(analysis.projectName(), results, 60, Readiness.REVIEW_NEEDED,
                (int) results.stream().filter(r -> r.primaryTurn().evaluation().verdict() == Verdict.SKIPPED).count()));
    }
}
