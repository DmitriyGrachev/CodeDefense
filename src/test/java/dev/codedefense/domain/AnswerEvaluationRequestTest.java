package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnswerEvaluationRequestTest {
    @Test
    void acceptsAndCanonicalizesPrimaryRequest() {
        TechnicalQuestion question = question();
        String answer = "a".repeat(8_000);

        AnswerEvaluationRequest request = new AnswerEvaluationRequest(
                " project ", " Java CLI ", " project summary ", question, EvaluationStage.PRIMARY,
                answer, question.prompt(), answer, Optional.empty());

        assertEquals("project", request.projectName());
        assertEquals("Java CLI", request.projectType());
        assertEquals("project summary", request.projectSummary());
        assertEquals(answer, request.primaryAnswer());
        assertEquals(answer, request.currentAnswer());
        assertEquals(Optional.empty(), request.previousEvaluation());
    }

    @Test
    void acceptsFollowUpRequestWithOriginalPrimaryAnswerAndPreviousEvaluation() {
        TechnicalQuestion question = question();
        AnswerEvaluation previous = partial();

        AnswerEvaluationRequest request = new AnswerEvaluationRequest(
                "project", "Java CLI", "summary", question, EvaluationStage.FOLLOW_UP,
                "original-answer", " focused-follow-up ", " follow-up-answer ", Optional.of(previous));

        assertEquals("original-answer", request.primaryAnswer());
        assertEquals("focused-follow-up", request.currentPrompt());
        assertEquals("follow-up-answer", request.currentAnswer());
        assertEquals(previous, request.previousEvaluation().orElseThrow());
    }

    @Test
    void enforcesPrimaryAndFollowUpStateInvariants() {
        TechnicalQuestion question = question();
        AnswerEvaluation previous = partial();

        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.PRIMARY, "answer", question.prompt(), "answer", Optional.of(previous)));
        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.PRIMARY, "answer", "different prompt", "answer", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.PRIMARY, "answer", question.prompt(), "different", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.FOLLOW_UP, "answer", "focused-follow-up", "follow-up", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.FOLLOW_UP, "answer", "wrong follow-up", "follow-up", Optional.of(previous)));
        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.FOLLOW_UP, "answer", "focused-follow-up", "follow-up",
                Optional.of(AnswerEvaluation.skipped())));
        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.PRIMARY, "x".repeat(8_001), question.prompt(), "x".repeat(8_001), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> request(
                EvaluationStage.PRIMARY, "answer", "x".repeat(501), "answer", Optional.empty()));
    }

    @Test
    void toStringDoesNotExposeAnswersOrGradingData() {
        TechnicalQuestion question = question();
        AnswerEvaluation previous = partial();
        AnswerEvaluationRequest request = new AnswerEvaluationRequest(
                "project", "Java CLI", "summary", question, EvaluationStage.FOLLOW_UP,
                "PRIMARY_ANSWER_SECRET", "focused-follow-up", "CURRENT_ANSWER_SECRET", Optional.of(previous));

        String rendered = request.toString();

        assertTrue(rendered.contains("FOLLOW_UP"));
        for (String privateValue : List.of(
                "PRIMARY_ANSWER_SECRET", "CURRENT_ANSWER_SECRET", "EXPECTED_KEY_SECRET",
                "PREVIOUS_FEEDBACK_SECRET", "focused-follow-up")) {
            assertFalse(rendered.contains(privateValue));
        }
    }

    private static AnswerEvaluationRequest request(
            EvaluationStage stage,
            String primaryAnswer,
            String currentPrompt,
            String currentAnswer,
            Optional<AnswerEvaluation> previous) {
        return new AnswerEvaluationRequest(
                "project", "Java CLI", "summary", question(), stage,
                primaryAnswer, currentPrompt, currentAnswer, previous);
    }

    private static AnswerEvaluation partial() {
        return new AnswerEvaluation(
                Verdict.PARTIAL, 60, "PREVIOUS_FEEDBACK_SECRET", List.of(), List.of("missing"),
                Optional.of("focused-follow-up"));
    }

    private static TechnicalQuestion question() {
        return new TechnicalQuestion(
                "q1", "How does the application start?", "Understand startup",
                List.of("EXPECTED_KEY_SECRET", "Second point"),
                List.of(new CodeEvidence("src/App.java", 1, 2, "Evidence reason")));
    }
}
