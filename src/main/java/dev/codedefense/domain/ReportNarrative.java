package dev.codedefense.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ReportNarrative(String headline, String summary, List<String> strengths,
                              List<String> knowledgeGaps, List<String> recommendedActions) {
    public ReportNarrative {
        headline = canonicalize(headline, "headline", 5, 160);
        summary = canonicalize(summary, "summary", 40, 1200);
        strengths = canonicalizeList(strengths, "strengths", 0, 6);
        knowledgeGaps = canonicalizeList(knowledgeGaps, "knowledgeGaps", 0, 6);
        recommendedActions = canonicalizeList(recommendedActions, "recommendedActions", 1, 6);
    }

    static String canonicalize(String value, String field, int minimum, int maximum) {
        Objects.requireNonNull(value, field);
        String normalized = value.replaceAll("\\s+", " ").strip();
        if (normalized.length() < minimum || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " has an invalid length");
        }
        return normalized;
    }

    private static List<String> canonicalizeList(List<String> values, String field, int minimum, int maximum) {
        Objects.requireNonNull(values, field);
        if (values.size() < minimum || values.size() > maximum) {
            throw new IllegalArgumentException(field + " has an invalid size");
        }
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            copy.add(canonicalize(value, field + " item", 3, 240));
        }
        return List.copyOf(copy);
    }

    @Override
    public String toString() {
        return "ReportNarrative[headlineLength=%d, summaryLength=%d, strengthCount=%d, knowledgeGapCount=%d, recommendedActionCount=%d]"
                .formatted(headline.length(), summary.length(), strengths.size(), knowledgeGaps.size(), recommendedActions.size());
    }
}
