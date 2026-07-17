package dev.codedefense.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AnswerEvaluation(
        Verdict verdict,
        int score,
        String feedback,
        List<String> understoodConcepts,
        List<String> missingConcepts,
        Optional<String> followUpQuestion) {
    private static final int MAXIMUM_CONCEPTS = 6;
    private static final int MAXIMUM_FOLLOW_UP_CHARACTERS = 500;

    public AnswerEvaluation {
        Objects.requireNonNull(verdict, "verdict");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        feedback = CodeEvidence.requireNonBlank(feedback, "feedback");
        understoodConcepts = copyConcepts(understoodConcepts, "understoodConcepts");
        missingConcepts = copyConcepts(missingConcepts, "missingConcepts");
        Objects.requireNonNull(followUpQuestion, "followUpQuestion");
        followUpQuestion = followUpQuestion.map(String::strip).filter(value -> !value.isBlank());
        if (followUpQuestion.map(String::length).orElse(0) > MAXIMUM_FOLLOW_UP_CHARACTERS) {
            throw new IllegalArgumentException("followUpQuestion exceeds the character limit");
        }
        if (verdict == Verdict.SKIPPED
                && (score != 0 || !understoodConcepts.isEmpty() || !missingConcepts.isEmpty()
                        || followUpQuestion.isPresent())) {
            throw new IllegalArgumentException("A skipped evaluation cannot contain grading data");
        }
    }

    public static AnswerEvaluation skipped() {
        return new AnswerEvaluation(
                Verdict.SKIPPED, 0, "Skipped by user.", List.of(), List.of(), Optional.empty());
    }

    private static List<String> copyConcepts(List<String> values, String field) {
        List<String> copy = CodeEvidence.copyNonBlankStrings(values, field);
        if (copy.size() > MAXIMUM_CONCEPTS) {
            throw new IllegalArgumentException(field + " cannot contain more than six entries");
        }
        return copy;
    }

    @Override
    public String toString() {
        return "AnswerEvaluation[verdict=%s, score=%d, understoodConceptCount=%d, missingConceptCount=%d, hasFollowUp=%s]"
                .formatted(verdict, score, understoodConcepts.size(), missingConcepts.size(), followUpQuestion.isPresent());
    }
}
