package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InterviewSessionTest {
    @Test
    void copiesExactlyThreeOrderedResultsAndCountsSkippedPrimaries() {
        List<QuestionResult> mutable = new ArrayList<>(List.of(
                result(1, primary(80), Optional.empty(), 80),
                result(2, skippedPrimary(), Optional.empty(), 0),
                result(3, primary(60), Optional.of(followUp(90)), 78)));

        InterviewSession session = new InterviewSession(
                " project ", mutable, 53, Readiness.KNOWLEDGE_GAPS, 1);
        mutable.clear();

        assertEquals("project", session.projectName());
        assertEquals(List.of(1, 2, 3), session.results().stream().map(QuestionResult::questionNumber).toList());
        assertEquals(1, session.skippedQuestionCount());
        assertThrows(UnsupportedOperationException.class, () -> session.results().clear());
    }

    @Test
    void enforcesTurnTypesScoresOrderAndSessionShape() {
        assertThrows(IllegalArgumentException.class, () -> result(0, primary(50), Optional.empty(), 50));
        assertThrows(IllegalArgumentException.class, () -> new QuestionResult(
                1, question("q1"), followUp(50), Optional.empty(), 50));
        assertThrows(IllegalArgumentException.class, () -> result(
                1, primary(50), Optional.of(primary(60)), 56));
        assertThrows(IllegalArgumentException.class, () -> result(
                1, skippedPrimary(), Optional.empty(), 1));

        List<QuestionResult> valid = validResults();
        assertThrows(IllegalArgumentException.class, () -> new InterviewSession(
                "project", valid.subList(0, 2), 50, Readiness.KNOWLEDGE_GAPS, 0));
        assertThrows(IllegalArgumentException.class, () -> new InterviewSession(
                "project", List.of(valid.get(1), valid.get(0), valid.get(2)),
                50, Readiness.KNOWLEDGE_GAPS, 0));
        assertThrows(IllegalArgumentException.class, () -> new InterviewSession(
                "project", valid, 50, Readiness.KNOWLEDGE_GAPS, 1));
        assertThrows(IllegalArgumentException.class, () -> new InterviewSession(
                "project", valid, 101, Readiness.STRONG_UNDERSTANDING, 0));
    }

    @Test
    void toStringsHidePromptsAnswersFeedbackAndExpectedKeyPoints() {
        InterviewTurn primary = primary(70);
        QuestionResult result = result(1, primary, Optional.empty(), 70);
        InterviewSession session = new InterviewSession(
                "project", List.of(result, result(2, primary(70), Optional.empty(), 70),
                        result(3, primary(70), Optional.empty(), 70)),
                70, Readiness.REVIEW_NEEDED, 0);

        for (String rendered : List.of(primary.toString(), result.toString(), session.toString())) {
            assertFalse(rendered.contains("QUESTION_PROMPT_SECRET"));
            assertFalse(rendered.contains("ANSWER_SECRET"));
            assertFalse(rendered.contains("FEEDBACK_SECRET"));
            assertFalse(rendered.contains("EXPECTED_KEY_SECRET"));
        }
        assertTrue(session.toString().contains("overallScore=70"));
    }

    private static List<QuestionResult> validResults() {
        return List.of(
                result(1, primary(50), Optional.empty(), 50),
                result(2, primary(60), Optional.empty(), 60),
                result(3, primary(70), Optional.empty(), 70));
    }

    private static QuestionResult result(
            int number, InterviewTurn primary, Optional<InterviewTurn> followUp, int score) {
        return new QuestionResult(number, question("q" + number), primary, followUp, score);
    }

    private static InterviewTurn primary(int score) {
        return new InterviewTurn(
                TurnType.PRIMARY, "QUESTION_PROMPT_SECRET", "ANSWER_SECRET",
                new AnswerEvaluation(Verdict.PARTIAL, score, "FEEDBACK_SECRET",
                        List.of(), List.of(), Optional.empty()));
    }

    private static InterviewTurn skippedPrimary() {
        return new InterviewTurn(TurnType.PRIMARY, "QUESTION_PROMPT_SECRET", "skip", AnswerEvaluation.skipped());
    }

    private static InterviewTurn followUp(int score) {
        return new InterviewTurn(
                TurnType.FOLLOW_UP, "FOLLOW_UP_SECRET", "FOLLOW_UP_ANSWER_SECRET",
                new AnswerEvaluation(Verdict.CORRECT, score, "FOLLOW_UP_FEEDBACK_SECRET",
                        List.of(), List.of(), Optional.empty()));
    }

    private static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(
                id, "QUESTION_PROMPT_SECRET", "Learning goal",
                List.of("EXPECTED_KEY_SECRET", "Second point"),
                List.of(new CodeEvidence("src/App.java", 1, 2, "Evidence reason")));
    }
}
