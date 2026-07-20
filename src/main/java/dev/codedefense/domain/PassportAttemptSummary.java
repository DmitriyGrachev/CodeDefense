package dev.codedefense.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PassportAttemptSummary(
        PassportAttemptId attemptId,
        Optional<PassportAttemptId> supersedes,
        int attemptNumber,
        String diffFingerprint,
        Instant createdAt,
        int overallScore,
        Readiness readiness,
        List<PassportCategoryReceipt> categories) {
    public PassportAttemptSummary {
        Objects.requireNonNull(attemptId); Objects.requireNonNull(supersedes);
        if (attemptNumber < 1 || attemptNumber == 1 && supersedes.isPresent()
                || attemptNumber > 1 && supersedes.isEmpty()
                || supersedes.filter(attemptId::equals).isPresent()) {
            throw new IllegalArgumentException("attempt lineage is invalid");
        }
        if (diffFingerprint == null || !diffFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("diffFingerprint is invalid");
        }
        Objects.requireNonNull(createdAt); Objects.requireNonNull(readiness);
        if (overallScore < 0 || overallScore > 100) throw new IllegalArgumentException("score is invalid");
        categories = List.copyOf(categories);
        if (categories.size() != 3 || categories.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("three categories are required");
        }
    }
    @Override public String toString() {
        return "PassportAttemptSummary[attemptId=%s, attemptNumber=%d, fingerprint=%s, score=%d, readiness=%s]"
                .formatted(attemptId, attemptNumber, diffFingerprint, overallScore, readiness);
    }
}
