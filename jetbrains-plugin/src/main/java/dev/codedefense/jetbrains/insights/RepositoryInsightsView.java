package dev.codedefense.jetbrains.insights;

import java.util.List;
import java.util.Set;

/** Validated, source-free repository learning insights for local display. */
public record RepositoryInsightsView(
        int schemaVersion,
        int attemptCount,
        int defendedChangeCount,
        List<CategoryInsightView> categories,
        String strongestCategory,
        String practiceCategory,
        List<Integer> recentOverallScores) {
    private static final List<String> CATEGORY_ORDER = List.of("decision", "counterfactual", "test-prediction");
    private static final Set<String> CATEGORY_IDS = Set.copyOf(CATEGORY_ORDER);

    public RepositoryInsightsView {
        if (schemaVersion != 1 || attemptCount < 0 || attemptCount > 20
                || defendedChangeCount < 0 || defendedChangeCount > attemptCount) throw invalid();
        if (categories == null || recentOverallScores == null) throw invalid();
        categories = List.copyOf(categories);
        if (categories.size() != CATEGORY_ORDER.size()) throw invalid();
        for (int index = 0; index < CATEGORY_ORDER.size(); index++) {
            CategoryInsightView category = categories.get(index);
            if (category == null || !CATEGORY_ORDER.get(index).equals(category.id())) throw invalid();
        }
        if (strongestCategory == null || practiceCategory == null) throw invalid();
        if (attemptCount == 0) {
            if (!strongestCategory.isEmpty() || !practiceCategory.isEmpty()) throw invalid();
        } else if (!CATEGORY_IDS.contains(strongestCategory) || !CATEGORY_IDS.contains(practiceCategory)) {
            throw invalid();
        }
        recentOverallScores = List.copyOf(recentOverallScores);
        if (recentOverallScores.size() > 10) throw invalid();
        for (Integer score : recentOverallScores) {
            if (score == null || score < 0 || score > 100) throw invalid();
        }
    }

    @Override
    public String toString() {
        return "RepositoryInsightsView[schemaVersion=%d, attemptCount=%d, defendedChangeCount=%d, "
                .formatted(schemaVersion, attemptCount, defendedChangeCount)
                + "categoryCount=%d, recentScoreCount=%d]".formatted(categories.size(), recentOverallScores.size());
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Repository insights response is invalid.");
    }
}
