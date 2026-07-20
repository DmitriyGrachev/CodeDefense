package dev.codedefense.domain;

import java.util.Objects;
import java.util.Optional;

public record PassportCategoryReceipt(
        String id,
        Verdict primaryVerdict,
        int primaryScore,
        Optional<Verdict> followUpVerdict,
        Optional<Integer> followUpScore,
        int finalScore) {
    public PassportCategoryReceipt {
        id = requireNonBlank(id, "id");
        Objects.requireNonNull(primaryVerdict, "primaryVerdict");
        Objects.requireNonNull(followUpVerdict, "followUpVerdict");
        Objects.requireNonNull(followUpScore, "followUpScore");
        requireScore(primaryScore, "primaryScore");
        requireScore(finalScore, "finalScore");
        followUpScore.ifPresent(score -> requireScore(score, "followUpScore"));
        if (followUpVerdict.isPresent() != followUpScore.isPresent()) {
            throw new IllegalArgumentException("follow-up verdict and score must appear together");
        }
    }

    static PassportCategoryReceipt from(QuestionResult result) {
        AnswerEvaluation primary = result.primaryTurn().evaluation();
        return new PassportCategoryReceipt(result.question().id(), primary.verdict(), primary.score(),
                result.followUpTurn().map(turn -> turn.evaluation().verdict()),
                result.followUpTurn().map(turn -> turn.evaluation().score()), result.finalScore());
    }

    private static void requireScore(int score, String field) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank");
        }
        return value.strip();
    }
}
