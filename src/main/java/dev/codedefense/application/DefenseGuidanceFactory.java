package dev.codedefense.application;

import dev.codedefense.domain.DefenseGuidance;
import dev.codedefense.domain.Verdict;
import java.util.Objects;

public final class DefenseGuidanceFactory {
    public DefenseGuidance create(String categoryId, Verdict verdict, int score) {
        Objects.requireNonNull(verdict, "verdict");
        if (score < 0 || score > 100) throw new IllegalArgumentException("score is invalid");
        String topic = switch (categoryId) {
            case "decision" -> "the design decision and its trade-offs";
            case "counterfactual" -> "failure behavior and alternative outcomes";
            case "test-prediction" -> "observable tests and regression boundaries";
            default -> throw new IllegalArgumentException("unknown category");
        };
        String action = switch (verdict) {
            case SKIPPED -> "Start by explaining " + topic + ".";
            case INCORRECT -> "Revisit the changed evidence, then explain " + topic + ".";
            case PARTIAL -> "Strengthen your explanation of " + topic + " with one concrete example.";
            case CORRECT -> "Keep the strong explanation of " + topic + " and test one edge case.";
        };
        return new DefenseGuidance(categoryId, action);
    }
}
