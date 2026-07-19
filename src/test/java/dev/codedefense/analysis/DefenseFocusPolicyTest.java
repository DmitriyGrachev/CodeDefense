package dev.codedefense.analysis;

import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.domain.DefenseFocus;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DefenseFocusPolicyTest {
    @Test void exposesFourClosedCaseInsensitiveModes() {
        assertEquals(DefenseFocus.FAILURE_MODES, DefenseFocus.parse("FAILURE-MODES"));
        assertEquals("Testing", DefenseFocus.TESTING.displayName());
        assertEquals(4, DefenseFocus.values().length);
        assertThrows(IllegalArgumentException.class, () -> DefenseFocus.parse("custom prompt"));
    }
    @Test void policyIsImmutableBoundedAndCoversAllCategories() {
        DefenseFocusPolicy policy = DefenseFocusPolicy.forFocus(DefenseFocus.ARCHITECTURE);
        assertTrue(policy.analysisInstruction().length() < 1000);
        assertEquals(3, policy.requiredAngles().size());
        assertThrows(UnsupportedOperationException.class, () -> policy.requiredAngles().add("x"));
        assertFalse(policy.toString().contains(policy.analysisInstruction()));
    }
}
