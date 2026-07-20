package dev.codedefense.passport;

import dev.codedefense.domain.CategoryLearningInsight;
import dev.codedefense.domain.RepositoryLearningInsights;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Deterministic source-free JSON boundary for repository-local learning insights. */
public final class RepositoryLearningInsightsJsonCodec {
    public static final int MAXIMUM_OUTPUT_BYTES = 256 * 1024;

    public byte[] encode(RepositoryLearningInsights insights) {
        Objects.requireNonNull(insights, "insights");
        StringBuilder json = new StringBuilder(384);
        json.append("{\"schemaVersion\":").append(insights.schemaVersion())
                .append(",\"attemptCount\":").append(insights.attemptCount())
                .append(",\"defendedChangeCount\":").append(insights.defendedChangeCount())
                .append(",\"categories\":[");
        for (int index = 0; index < insights.categories().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            CategoryLearningInsight category = insights.categories().get(index);
            json.append("{\"id\":\"").append(category.id())
                    .append("\",\"averageScore\":").append(category.averageScore()).append('}');
        }
        json.append("],\"strongestCategory\":\"").append(insights.strongestCategory())
                .append("\",\"practiceCategory\":\"").append(insights.practiceCategory())
                .append("\",\"recentOverallScores\":[");
        for (int index = 0; index < insights.recentOverallScores().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(insights.recentOverallScores().get(index));
        }
        json.append("]}\n");
        byte[] encoded = json.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_OUTPUT_BYTES) {
            throw new IllegalArgumentException("Repository learning insights output exceeds the maximum size");
        }
        return encoded;
    }
}
