package dev.codedefense.interview;

import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.TurnType;
import dev.codedefense.domain.Verdict;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class InterviewScorer {
    public int calculateQuestionScore(InterviewTurn primary, Optional<InterviewTurn> followUp) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(followUp, "followUp");
        if (primary.type() != TurnType.PRIMARY) {
            throw new IllegalArgumentException("Expected a primary turn");
        }
        if (followUp.filter(turn -> turn.type() != TurnType.FOLLOW_UP).isPresent()) {
            throw new IllegalArgumentException("Expected a follow-up turn");
        }
        if (primary.evaluation().verdict() == Verdict.SKIPPED) {
            return 0;
        }
        if (followUp.isEmpty() || followUp.orElseThrow().evaluation().verdict() == Verdict.SKIPPED) {
            return primary.evaluation().score();
        }
        int primaryScore = primary.evaluation().score();
        int followUpScore = followUp.orElseThrow().evaluation().score();
        if (followUpScore <= primaryScore) {
            return primaryScore;
        }
        return (int) Math.round(primaryScore * 0.4 + followUpScore * 0.6);
    }

    public int calculateOverallScore(List<QuestionResult> results) {
        Objects.requireNonNull(results, "results");
        if (results.size() != 3 || results.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Exactly three question results are required");
        }
        return (int) Math.round(results.stream().mapToInt(QuestionResult::finalScore).average().orElseThrow());
    }
}
