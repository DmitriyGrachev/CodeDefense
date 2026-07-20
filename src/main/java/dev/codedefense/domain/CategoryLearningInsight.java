package dev.codedefense.domain;

import java.util.Set;

/** Source-free aggregate score for one fixed technical-defense category. */
public record CategoryLearningInsight(String id, int averageScore) {
    private static final Set<String> IDS = Set.of(
            "decision", "counterfactual", "test-prediction");

    public CategoryLearningInsight {
        if (id == null || !IDS.contains(id)) {
            throw new IllegalArgumentException("id must be a supported category");
        }
        if (averageScore < 0 || averageScore > 100) {
            throw new IllegalArgumentException("averageScore must be between 0 and 100");
        }
    }
}
