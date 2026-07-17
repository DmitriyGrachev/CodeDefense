package dev.codedefense.interview;

import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnswerEvaluationValidatorTest {
    private final AnswerEvaluationValidator validator = new AnswerEvaluationValidator();
    @Test void validatesAndCanonicalizesARepositorySpecificEvaluation() {
        AnswerEvaluation result = validator.validate(payload("PARTIAL", 60, List.of("  Flow   control "), List.of("Errors"), "Why on failure?"), request());
        assertEquals(List.of("Flow control"), result.understoodConcepts());
        assertEquals(Optional.of("Why on failure?"), result.followUpQuestion());
    }
    @Test void rejectsVerdictBandsDuplicatesIntersectionsSkippedAndUnsafeFollowups() {
        List<AnswerEvaluationValidator.Payload> invalid = List.of(
                payload("CORRECT", 79, List.of(), List.of(), ""), payload("PARTIAL", 39, List.of(), List.of(), ""),
                payload("INCORRECT", 40, List.of(), List.of(), ""), payload("SKIPPED", 0, List.of(), List.of(), ""),
                payload("CORRECT", 90, List.of(), List.of(), "extra"),
                payload("PARTIAL", 60, List.of("Same", " same "), List.of(), ""),
                payload("PARTIAL", 60, List.of("Same"), List.of(" SAME "), ""),
                payload("PARTIAL", 60, List.of(), List.of(), request().currentPrompt()));
        for (var value : invalid) {
            InvalidCodexResponseException error = assertThrows(InvalidCodexResponseException.class, () -> validator.validate(value, request()));
            assertEquals("Codex returned an invalid answer evaluation.", error.getMessage());
        }
    }
    private static AnswerEvaluationValidator.Payload payload(String verdict, int score, List<String> understood, List<String> missing, String followUp) {
        return new AnswerEvaluationValidator.Payload(verdict, score, "Useful concise feedback", understood, missing, followUp);
    }
    private static AnswerEvaluationRequest request() { return AnswerEvaluationPromptFactoryTest.request("project", "answer"); }
}
