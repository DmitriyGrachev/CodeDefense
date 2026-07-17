package dev.codedefense.domain;

import java.util.Objects;
import java.util.Optional;

public record AnswerEvaluationRequest(
        String projectName,
        String projectType,
        String projectSummary,
        TechnicalQuestion primaryQuestion,
        EvaluationStage stage,
        String primaryAnswer,
        String currentPrompt,
        String currentAnswer,
        Optional<AnswerEvaluation> previousEvaluation) {
    private static final int MAXIMUM_ANSWER_CHARACTERS = 8_000;
    private static final int MAXIMUM_PROMPT_CHARACTERS = 500;

    public AnswerEvaluationRequest {
        projectName = CodeEvidence.requireNonBlank(projectName, "projectName");
        projectType = CodeEvidence.requireNonBlank(projectType, "projectType");
        projectSummary = CodeEvidence.requireNonBlank(projectSummary, "projectSummary");
        Objects.requireNonNull(primaryQuestion, "primaryQuestion");
        Objects.requireNonNull(stage, "stage");
        primaryAnswer = bounded(primaryAnswer, "primaryAnswer", MAXIMUM_ANSWER_CHARACTERS);
        currentPrompt = bounded(currentPrompt, "currentPrompt", MAXIMUM_PROMPT_CHARACTERS);
        currentAnswer = bounded(currentAnswer, "currentAnswer", MAXIMUM_ANSWER_CHARACTERS);
        Objects.requireNonNull(previousEvaluation, "previousEvaluation");

        if (stage == EvaluationStage.PRIMARY) {
            if (previousEvaluation.isPresent()
                    || !currentPrompt.equals(primaryQuestion.prompt())
                    || !currentAnswer.equals(primaryAnswer)) {
                throw new IllegalArgumentException("Primary evaluation state is inconsistent");
            }
        } else {
            AnswerEvaluation previous = previousEvaluation.orElseThrow(
                    () -> new IllegalArgumentException("Follow-up evaluation requires a previous evaluation"));
            if (previous.verdict() == Verdict.SKIPPED
                    || previous.followUpQuestion().isEmpty()
                    || !currentPrompt.equals(previous.followUpQuestion().orElseThrow())) {
                throw new IllegalArgumentException("Follow-up evaluation state is inconsistent");
            }
        }
    }

    private static String bounded(String value, String field, int maximumCharacters) {
        String canonical = CodeEvidence.requireNonBlank(value, field);
        if (canonical.length() > maximumCharacters) {
            throw new IllegalArgumentException(field + " exceeds the character limit");
        }
        return canonical;
    }

    @Override
    public String toString() {
        return "AnswerEvaluationRequest[stage=%s, questionId=%s, primaryAnswerLength=%d, currentAnswerLength=%d, hasPreviousEvaluation=%s]"
                .formatted(stage, primaryQuestion.id(), primaryAnswer.length(), currentAnswer.length(), previousEvaluation.isPresent());
    }
}
