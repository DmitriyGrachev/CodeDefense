package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryLearningInsightsTest {
    private static final List<CategoryLearningInsight> CATEGORIES = List.of(
            new CategoryLearningInsight("decision", 74),
            new CategoryLearningInsight("counterfactual", 50),
            new CategoryLearningInsight("test-prediction", 25));

    @Test
    void acceptsTheVersionedBoundedShape() {
        RepositoryLearningInsights insights = new RepositoryLearningInsights(
                1, 20, 18, CATEGORIES, "decision", "test-prediction",
                List.of(0, 25, 50, 75, 100));

        assertEquals(1, insights.schemaVersion());
        assertEquals(20, insights.attemptCount());
        assertEquals(18, insights.defendedChangeCount());
    }

    @Test
    void rejectsUnsupportedSchemaAndInvalidCounts() {
        assertInvalid(() -> insights(0, 0, 0, CATEGORIES, "", "", List.of()));
        assertInvalid(() -> insights(2, 0, 0, CATEGORIES, "", "", List.of()));
        assertInvalid(() -> insights(1, -1, 0, CATEGORIES, "decision", "decision", List.of()));
        assertInvalid(() -> insights(1, 21, 0, CATEGORIES, "decision", "decision", List.of()));
        assertInvalid(() -> insights(1, 1, -1, CATEGORIES, "decision", "decision", List.of()));
        assertInvalid(() -> insights(1, 1, 2, CATEGORIES, "decision", "decision", List.of()));
    }

    @Test
    void requiresExactCategoryOrderAndBoundedScores() {
        assertInvalid(() -> insights(1, 1, 1, List.of(
                new CategoryLearningInsight("counterfactual", 50),
                new CategoryLearningInsight("decision", 74),
                new CategoryLearningInsight("test-prediction", 25)),
                "decision", "test-prediction", List.of(50)));
        assertInvalid(() -> new CategoryLearningInsight("unknown", 50));
        assertInvalid(() -> new CategoryLearningInsight("decision", -1));
        assertInvalid(() -> new CategoryLearningInsight("decision", 101));
    }

    @Test
    void emptyHistoryHasNoCategoryInsightsOrLabels() {
        RepositoryLearningInsights insights = insights(1, 0, 0, CATEGORIES, "", "", List.of());

        assertEquals(CATEGORIES, insights.categories());
        assertEquals("", insights.strongestCategory());
        assertEquals("", insights.practiceCategory());

        assertInvalid(() -> insights(1, 0, 0, CATEGORIES, "decision", "test-prediction", List.of()));
        assertInvalid(() -> insights(1, 0, 0, List.of(), "", "", List.of()));
    }

    @Test
    void nonemptyHistoryRequiresValidCategoryLabels() {
        assertInvalid(() -> insights(1, 1, 1, CATEGORIES, "", "test-prediction", List.of(50)));
        assertInvalid(() -> insights(1, 1, 1, CATEGORIES, "unknown", "test-prediction", List.of(50)));
        assertInvalid(() -> insights(1, 1, 1, CATEGORIES, "decision", "unknown", List.of(50)));
    }

    @Test
    void recentScoresAreBoundedAndValidated() {
        assertInvalid(() -> insights(1, 1, 1, CATEGORIES, "decision", "test-prediction",
                List.of(-1)));
        assertInvalid(() -> insights(1, 1, 1, CATEGORIES, "decision", "test-prediction",
                List.of(101)));
        assertInvalid(() -> insights(1, 11, 1, CATEGORIES, "decision", "test-prediction",
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)));
    }

    @Test
    void defensivelyCopiesListsAndKeepsToStringSourceFree() {
        ArrayList<CategoryLearningInsight> categories = new ArrayList<>(CATEGORIES);
        ArrayList<Integer> recent = new ArrayList<>(List.of(33, 61, 84));
        RepositoryLearningInsights insights = insights(
                1, 3, 2, categories, "decision", "test-prediction", recent);

        categories.clear();
        recent.clear();

        assertEquals(CATEGORIES, insights.categories());
        assertEquals(List.of(33, 61, 84), insights.recentOverallScores());
        assertThrows(UnsupportedOperationException.class,
                () -> insights.categories().add(new CategoryLearningInsight("decision", 1)));
        assertThrows(UnsupportedOperationException.class,
                () -> insights.recentOverallScores().add(1));
        assertEquals("RepositoryLearningInsights[schemaVersion=1, attemptCount=3, defendedChangeCount=2]",
                insights.toString());
        assertFalse(insights.toString().contains("decision"));
        assertFalse(insights.toString().contains("84"));
    }

    private static RepositoryLearningInsights insights(int schemaVersion, int attempts,
            int changes, List<CategoryLearningInsight> categories, String strongest,
            String practice, List<Integer> recent) {
        return new RepositoryLearningInsights(schemaVersion, attempts, changes, categories,
                strongest, practice, recent);
    }

    private static void assertInvalid(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }
}
