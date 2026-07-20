package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangeHandoffTest {
 @Test void validatesBoundedContiguousSourceFreeHistory() {
  var categories = List.of(
    new PassportCategoryReceipt("decision", Verdict.PARTIAL, 50, Optional.empty(), Optional.empty(), 50),
    new PassportCategoryReceipt("counterfactual", Verdict.PARTIAL, 50, Optional.empty(), Optional.empty(), 50),
    new PassportCategoryReceipt("test-prediction", Verdict.PARTIAL, 50, Optional.empty(), Optional.empty(), 50));
  var id = new PassportAttemptId("11111111-1111-4111-8111-111111111111");
  var attempt = new PassportAttemptSummary(id, Optional.empty(), 1, "d".repeat(64), Instant.EPOCH,
    50, Readiness.REVIEW_NEEDED, categories);
  var handoff = new ChangeHandoff(1, "22222222-2222-4222-8222-222222222222", Instant.EPOCH,
    "a".repeat(64), ChangeKind.STAGED, "b".repeat(40), "c".repeat(64), "d".repeat(64),
    List.of(attempt), "e".repeat(64));
  assertEquals(1, handoff.attempts().size());
  assertFalse(handoff.toString().contains("decision"));
  assertThrows(UnsupportedOperationException.class, () -> handoff.attempts().add(attempt));
 }
}
