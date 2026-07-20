package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.codedefense.domain.Verdict;
import org.junit.jupiter.api.Test;

class DefenseGuidanceFactoryTest {
    @Test
    void guidanceIsDeterministicAndContainsNoModelText() {
        DefenseGuidanceFactory factory = new DefenseGuidanceFactory();
        var first = factory.create("counterfactual", Verdict.PARTIAL, 55);
        var second = factory.create("counterfactual", Verdict.PARTIAL, 55);
        assertEquals(first, second);
        assertEquals("counterfactual", first.categoryId());
        assertFalse(first.message().contains("SECRET_MODEL_FEEDBACK"));
        assertFalse(first.message().toLowerCase().contains("approved"));
    }
}
