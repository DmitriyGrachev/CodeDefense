package dev.codedefense.domain;

import java.util.OptionalInt;

public record EvidenceCoverageSummary(int totalHunks, int measurableHunks, int referencedHunks) {
    public EvidenceCoverageSummary {
        if (totalHunks < 0 || measurableHunks < 0 || referencedHunks < 0
                || measurableHunks > totalHunks || referencedHunks > measurableHunks) {
            throw new IllegalArgumentException("Evidence coverage counts are inconsistent");
        }
    }

    public int unreferencedHunks() {
        return measurableHunks - referencedHunks;
    }

    public OptionalInt percentage() {
        return measurableHunks == 0 ? OptionalInt.empty()
                : OptionalInt.of((int) Math.round(referencedHunks * 100.0 / measurableHunks));
    }
}
