package dev.codedefense.passport;

import dev.codedefense.domain.CategoryLearningInsight;
import dev.codedefense.domain.RepositoryLearningInsights;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryLearningInsightsJsonCodecTest {
    @Test
    void encodesExactDeterministicNewlineTerminatedUtf8Json() {
        RepositoryLearningInsights insights = insights();

        byte[] encoded = new RepositoryLearningInsightsJsonCodec().encode(insights);

        assertArrayEquals(("{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,"
                + "\"categories\":[{\"id\":\"decision\",\"averageScore\":92},"
                + "{\"id\":\"counterfactual\",\"averageScore\":54},"
                + "{\"id\":\"test-prediction\",\"averageScore\":31}],"
                + "\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\","
                + "\"recentOverallScores\":[33,61,84]}\n").getBytes(StandardCharsets.UTF_8), encoded);
        assertTrue(encoded.length <= RepositoryLearningInsightsJsonCodec.MAXIMUM_OUTPUT_BYTES);
    }

    @Test
    void outputContainsNoPrivateOrModelControlledFields() {
        String json = new String(new RepositoryLearningInsightsJsonCodec().encode(insights()), StandardCharsets.UTF_8);

        for (String forbidden : List.of("project", "root", "timestamp", "source", "path", "question",
                "answer", "feedback", "evidence", "model", "user", "expectedKeyPoints")) {
            assertFalse(json.contains(forbidden), forbidden);
        }
    }

    private static RepositoryLearningInsights insights() {
        return new RepositoryLearningInsights(1, 3, 2, List.of(
                new CategoryLearningInsight("decision", 92),
                new CategoryLearningInsight("counterfactual", 54),
                new CategoryLearningInsight("test-prediction", 31)),
                "decision", "test-prediction", List.of(33, 61, 84));
    }
}
