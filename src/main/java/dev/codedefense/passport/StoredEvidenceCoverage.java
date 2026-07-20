package dev.codedefense.passport;

import dev.codedefense.domain.EvidenceCoverageMap;
import java.util.Objects;

public record StoredEvidenceCoverage(String receiptId, EvidenceCoverageMap coverage) {
    public StoredEvidenceCoverage {
        Objects.requireNonNull(receiptId, "receiptId");
        try {
            if (!java.util.UUID.fromString(receiptId).toString().equals(receiptId)) {
                throw new IllegalArgumentException("receiptId is invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("receiptId is invalid", exception);
        }
        Objects.requireNonNull(coverage, "coverage");
    }
}
