package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnswerEvaluationTest {
    @Test
    void stripsAndCopiesValuesWithoutExposingPrivateContent() {
        List<String> understood = new ArrayList<>(List.of(" understood-secret "));
        List<String> missing = new ArrayList<>(List.of(" missing-secret "));

        AnswerEvaluation evaluation = new AnswerEvaluation(
                Verdict.PARTIAL,
                55,
                " private-feedback ",
                understood,
                missing,
                Optional.of(" private-follow-up "));
        understood.add("later");
        missing.clear();

        assertEquals("private-feedback", evaluation.feedback());
        assertEquals(List.of("understood-secret"), evaluation.understoodConcepts());
        assertEquals(List.of("missing-secret"), evaluation.missingConcepts());
        assertEquals(Optional.of("private-follow-up"), evaluation.followUpQuestion());
        assertThrows(UnsupportedOperationException.class, () -> evaluation.understoodConcepts().add("mutate"));
        String rendered = evaluation.toString();
        assertTrue(rendered.contains("PARTIAL"));
        assertTrue(rendered.contains("score=55"));
        for (String privateValue : List.of(
                "private-feedback", "understood-secret", "missing-secret", "private-follow-up")) {
            assertFalse(rendered.contains(privateValue));
        }
    }

    @Test
    void skippedFactoryProducesTheOnlyValidSkippedShape() {
        AnswerEvaluation skipped = AnswerEvaluation.skipped();

        assertEquals(Verdict.SKIPPED, skipped.verdict());
        assertEquals(0, skipped.score());
        assertEquals("Skipped by user.", skipped.feedback());
        assertEquals(List.of(), skipped.understoodConcepts());
        assertEquals(List.of(), skipped.missingConcepts());
        assertEquals(Optional.empty(), skipped.followUpQuestion());

        assertThrows(IllegalArgumentException.class, () -> new AnswerEvaluation(
                Verdict.SKIPPED, 1, "Skipped", List.of(), List.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AnswerEvaluation(
                Verdict.SKIPPED, 0, "Skipped", List.of("concept"), List.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AnswerEvaluation(
                Verdict.SKIPPED, 0, "Skipped", List.of(), List.of("concept"), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AnswerEvaluation(
                Verdict.SKIPPED, 0, "Skipped", List.of(), List.of(), Optional.of("follow-up")));
    }

    @Test
    void enforcesScoreConceptAndFollowUpBounds() {
        assertThrows(IllegalArgumentException.class, () -> evaluation(-1, List.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> evaluation(101, List.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> evaluation(50, java.util.Collections.nCopies(7, "x"), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> evaluation(50, List.of(" "), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> evaluation(50, List.of(), Optional.of("x".repeat(501))));
        assertThrows(NullPointerException.class, () -> new AnswerEvaluation(
                null, 50, "Feedback", List.of(), List.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new AnswerEvaluation(
                Verdict.PARTIAL, 50, " ", List.of(), List.of(), Optional.empty()));

        assertEquals(0, evaluation(0, List.of(), Optional.empty()).score());
        assertEquals(100, evaluation(100, List.of(), Optional.empty()).score());
        assertEquals(Optional.empty(), evaluation(50, List.of(), Optional.of("   ")).followUpQuestion());
    }

    private static AnswerEvaluation evaluation(int score, List<String> understood, Optional<String> followUp) {
        return new AnswerEvaluation(Verdict.PARTIAL, score, "Useful feedback", understood, List.of(), followUp);
    }
}
