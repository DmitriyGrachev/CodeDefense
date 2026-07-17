package dev.codedefense.domain;

import java.util.Objects;
import java.util.Optional;

public record QuestionResult(
        int questionNumber,
        TechnicalQuestion question,
        InterviewTurn primaryTurn,
        Optional<InterviewTurn> followUpTurn,
        int finalScore) {
    public QuestionResult {
        if (questionNumber < 1 || questionNumber > 3) {
            throw new IllegalArgumentException("questionNumber must be between one and three");
        }
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(primaryTurn, "primaryTurn");
        Objects.requireNonNull(followUpTurn, "followUpTurn");
        if (primaryTurn.type() != TurnType.PRIMARY) {
            throw new IllegalArgumentException("primaryTurn must be a primary turn");
        }
        if (followUpTurn.filter(turn -> turn.type() != TurnType.FOLLOW_UP).isPresent()) {
            throw new IllegalArgumentException("followUpTurn must be a follow-up turn");
        }
        if (finalScore < 0 || finalScore > 100) {
            throw new IllegalArgumentException("finalScore must be between 0 and 100");
        }
        if (primaryTurn.evaluation().verdict() == Verdict.SKIPPED && finalScore != 0) {
            throw new IllegalArgumentException("A skipped primary question has a zero score");
        }
    }

    @Override
    public String toString() {
        return "QuestionResult[questionNumber=%d, questionId=%s, finalScore=%d, hasFollowUp=%s]"
                .formatted(questionNumber, question.id(), finalScore, followUpTurn.isPresent());
    }
}
