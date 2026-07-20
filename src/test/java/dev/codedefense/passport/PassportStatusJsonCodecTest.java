package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.application.PassportStatusView;
import dev.codedefense.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PassportStatusJsonCodecTest {
    @Test void rendersDeterministicOneLineSourceFreeStatus() {
        var categories = List.of(
                new PassportCategoryReceipt("decision", Verdict.PARTIAL, 60, Optional.empty(), Optional.empty(), 60),
                new PassportCategoryReceipt("counterfactual", Verdict.CORRECT, 90, Optional.empty(), Optional.empty(), 90),
                new PassportCategoryReceipt("test-prediction", Verdict.SKIPPED, 0, Optional.empty(), Optional.empty(), 0));
        var view = new PassportStatusView(1, true, PassportStatus.CURRENT, "STAGED", "abcdef123456",
                "testing", 2, 50, "REVIEW_NEEDED", categories);
        String json = new String(new PassportStatusJsonCodec().encode(view), StandardCharsets.UTF_8);
        assertTrue(json.endsWith("\n")); assertEquals(1, json.lines().count());
        assertTrue(json.contains("\"focus\":\"testing\""));
        assertFalse(json.contains("answer")); assertFalse(json.contains("feedback"));
    }
}
