package dev.codedefense.provenance;

import java.util.List;
import java.util.Objects;

public record NormalizedChangeEvidence(String relativePath, String status,
        List<String> hunkFingerprints) {
    public NormalizedChangeEvidence {
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
        status = Objects.requireNonNull(status, "status");
        Objects.requireNonNull(hunkFingerprints, "hunkFingerprints");
        hunkFingerprints = List.copyOf(hunkFingerprints);
        if (hunkFingerprints.isEmpty() || hunkFingerprints.stream()
                .anyMatch(value -> value == null || !value.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("hunk fingerprints are invalid");
        }
    }

    @Override public String toString() {
        return "NormalizedChangeEvidence[status=%s, hunkCount=%d]"
                .formatted(status, hunkFingerprints.size());
    }
}
