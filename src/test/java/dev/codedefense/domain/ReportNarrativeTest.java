package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportNarrativeTest {
    @Test
    void canonicalizesWhitespaceAndMakesListsImmutable() {
        List<String> strengths = new ArrayList<>(List.of("  clear\n architecture  "));
        ReportNarrative narrative = new ReportNarrative("  Useful project headline  ",
                "  This summary has enough detail to satisfy the minimum narrative length.  ", strengths,
                List.of(), List.of("  Add\tmore tests  "));

        strengths.clear();
        assertEquals("Useful project headline", narrative.headline());
        assertEquals("clear architecture", narrative.strengths().getFirst());
        assertEquals("Add more tests", narrative.recommendedActions().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> narrative.strengths().clear());
    }

    @Test
    void enforcesNarrativeBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrative("short", "too short", List.of(), List.of(), List.of("action")));
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrative("A valid headline", "x".repeat(40), List.of(), List.of(), List.of()));
    }
}
