package dev.codedefense.jetbrains.insights;

import java.util.Set;

/** Source-free category score returned by the local CodeDefense CLI. */
public record CategoryInsightView(String id, int averageScore) {
    private static final Set<String> IDS = Set.of("decision", "counterfactual", "test-prediction");

    public CategoryInsightView {
        if (id == null || !IDS.contains(id) || averageScore < 0 || averageScore > 100) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Repository category insight is invalid.");
    }
}
