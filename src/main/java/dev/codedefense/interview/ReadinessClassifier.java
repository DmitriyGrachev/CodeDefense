package dev.codedefense.interview;

import dev.codedefense.domain.Readiness;

public final class ReadinessClassifier {
    public Readiness classify(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        if (score >= 80) {
            return Readiness.STRONG_UNDERSTANDING;
        }
        if (score >= 55) {
            return Readiness.REVIEW_NEEDED;
        }
        return Readiness.KNOWLEDGE_GAPS;
    }
}
