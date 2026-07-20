package dev.codedefense.domain;

import java.util.List;
import java.util.Set;

/** Bounded, source-free learning aggregates for one repository identity. */
public record RepositoryLearningInsights(
        int schemaVersion,
        int attemptCount,
        int defendedChangeCount,
        List<CategoryLearningInsight> categories,
        String strongestCategory,
        String practiceCategory,
        List<Integer> recentOverallScores) {
    private static final List<String> CATEGORY_IDS =
            List.of("decision", "counterfactual", "test-prediction");
    private static final Set<String> CATEGORY_ID_SET = Set.copyOf(CATEGORY_IDS);

    public RepositoryLearningInsights {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("schemaVersion must be 1");
        }
        if (attemptCount < 0 || attemptCount > 20) {
            throw new IllegalArgumentException("attemptCount must be between 0 and 20");
        }
        if (defendedChangeCount < 0 || defendedChangeCount > attemptCount) {
            throw new IllegalArgumentException("defendedChangeCount must be between 0 and attemptCount");
        }
        if (categories == null) {
            throw new IllegalArgumentException("categories are required");
        }
        categories = List.copyOf(categories);
        if (categories.size() != CATEGORY_IDS.size()) {
            throw new IllegalArgumentException("exactly three categories are required");
        }
        for (int index = 0; index < CATEGORY_IDS.size(); index++) {
            CategoryLearningInsight category = categories.get(index);
            if (category == null || !CATEGORY_IDS.get(index).equals(category.id())) {
                throw new IllegalArgumentException("categories must use the required order");
            }
        }
        strongestCategory = validateLabel(strongestCategory, "strongestCategory");
        practiceCategory = validateLabel(practiceCategory, "practiceCategory");
        if (attemptCount == 0) {
            if (!strongestCategory.isEmpty() || !practiceCategory.isEmpty()) {
                throw new IllegalArgumentException("empty history cannot name categories");
            }
        } else if (!CATEGORY_ID_SET.contains(strongestCategory)
                || !CATEGORY_ID_SET.contains(practiceCategory)) {
            throw new IllegalArgumentException("nonempty history must name valid categories");
        }
        if (recentOverallScores == null) {
            throw new IllegalArgumentException("recentOverallScores are required");
        }
        recentOverallScores = List.copyOf(recentOverallScores);
        if (recentOverallScores.size() > 10 || recentOverallScores.stream()
                .anyMatch(score -> score == null || score < 0 || score > 100)) {
            throw new IllegalArgumentException("recentOverallScores must contain at most ten scores");
        }
    }

    private static String validateLabel(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    @Override
    public String toString() {
        return "RepositoryLearningInsights[schemaVersion=%d, attemptCount=%d, defendedChangeCount=%d]"
                .formatted(schemaVersion, attemptCount, defendedChangeCount);
    }
}
