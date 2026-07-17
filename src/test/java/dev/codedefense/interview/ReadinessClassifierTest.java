package dev.codedefense.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.domain.Readiness;
import org.junit.jupiter.api.Test;

class ReadinessClassifierTest {
    private final ReadinessClassifier classifier = new ReadinessClassifier();

    @Test
    void appliesExactReadinessThresholds() {
        assertEquals(Readiness.STRONG_UNDERSTANDING, classifier.classify(100));
        assertEquals(Readiness.STRONG_UNDERSTANDING, classifier.classify(80));
        assertEquals(Readiness.REVIEW_NEEDED, classifier.classify(79));
        assertEquals(Readiness.REVIEW_NEEDED, classifier.classify(55));
        assertEquals(Readiness.KNOWLEDGE_GAPS, classifier.classify(54));
        assertEquals(Readiness.KNOWLEDGE_GAPS, classifier.classify(0));
        assertThrows(IllegalArgumentException.class, () -> classifier.classify(-1));
        assertThrows(IllegalArgumentException.class, () -> classifier.classify(101));
    }

    @Test
    void readinessExposesOnlyHumanReadableNames() {
        assertEquals("Strong understanding", Readiness.STRONG_UNDERSTANDING.displayName());
        assertEquals("Review needed", Readiness.REVIEW_NEEDED.displayName());
        assertEquals("Knowledge gaps", Readiness.KNOWLEDGE_GAPS.displayName());
    }
}
