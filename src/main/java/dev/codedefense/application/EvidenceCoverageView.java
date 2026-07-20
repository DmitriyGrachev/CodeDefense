package dev.codedefense.application;

import dev.codedefense.domain.EvidenceCoverageMap;
import dev.codedefense.domain.EvidenceCoverageSummary;
import java.util.Objects;
import java.util.Optional;

public record EvidenceCoverageView(Optional<EvidenceCoverageSummary> summary,
        Optional<EvidenceCoverageMap> details, String status) {
    public EvidenceCoverageView {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(status, "status");
        if (status.isBlank() || details.isPresent() && summary.isEmpty()
                || details.isPresent() && !details.orElseThrow().summary().equals(summary.orElseThrow())) {
            throw new IllegalArgumentException("Evidence coverage view is inconsistent");
        }
    }
}
