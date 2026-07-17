package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.ReportNarrative;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReportNarrativeValidatorTest {
    @Test void rejectsDuplicatesIntersectionsAndScoreClaims() {
        ReportNarrativeValidator validator = new ReportNarrativeValidator();
        for (ReportNarrative value : List.of(
                Fixtures.narrative(List.of("Same", " same "), List.of(), List.of("Action")),
                Fixtures.narrative(List.of("Shared"), List.of(" shared "), List.of("Action")),
                new ReportNarrative("Useful report headline", "A sufficiently detailed summary that does not claim a score.", List.of(), List.of(), List.of("Improve your overall score")))) {
            InvalidCodexResponseException error = assertThrows(InvalidCodexResponseException.class, () -> validator.validate(value));
            assertEquals("Codex returned an invalid report narrative.", error.getMessage());
        }
    }
}
