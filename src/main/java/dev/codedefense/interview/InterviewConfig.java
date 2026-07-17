package dev.codedefense.interview;

import java.time.Duration;
import java.util.Objects;

public record InterviewConfig(
        int primaryQuestionCount,
        int maximumFollowUpsPerQuestion,
        int maximumAnswerCharacters,
        Duration evaluationTimeout) {
    public InterviewConfig {
        if (primaryQuestionCount != 3) {
            throw new IllegalArgumentException("The MVP requires exactly three primary questions");
        }
        if (maximumFollowUpsPerQuestion != 1) {
            throw new IllegalArgumentException("The MVP allows exactly one follow-up per question");
        }
        if (maximumAnswerCharacters <= 0) {
            throw new IllegalArgumentException("maximumAnswerCharacters must be positive");
        }
        Objects.requireNonNull(evaluationTimeout, "evaluationTimeout");
        if (evaluationTimeout.isZero() || evaluationTimeout.isNegative()) {
            throw new IllegalArgumentException("evaluationTimeout must be positive");
        }
    }

    public static InterviewConfig defaults() {
        return new InterviewConfig(3, 1, 8_000, Duration.ofSeconds(120));
    }
}
