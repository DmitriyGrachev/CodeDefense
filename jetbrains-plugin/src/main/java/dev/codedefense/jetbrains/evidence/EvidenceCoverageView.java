package dev.codedefense.jetbrains.evidence;

import dev.codedefense.jetbrains.process.EvidenceLocationView;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record EvidenceCoverageView(int totalHunks, int measurableHunks, int referencedHunks,
        List<Hunk> hunks) {
    public EvidenceCoverageView {
        if (totalHunks < 0 || measurableHunks < 0 || referencedHunks < 0
                || measurableHunks > totalHunks || referencedHunks > measurableHunks) {
            throw new IllegalArgumentException("Coverage counts are invalid");
        }
        Objects.requireNonNull(hunks, "hunks");
        hunks = List.copyOf(hunks);
        if (hunks.size() != totalHunks || hunks.size() > 256 || hunks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Coverage hunks are invalid");
        }
    }

    public OptionalInt percentage() {
        return measurableHunks == 0 ? OptionalInt.empty()
                : OptionalInt.of((int) Math.round(referencedHunks * 100.0 / measurableHunks));
    }

    public record Hunk(String relativePath, int ordinal, int startLine, int endLine,
            boolean navigable, String state, List<String> categoryIds) {
        public Hunk {
            new EvidenceLocationView(relativePath, startLine, endLine);
            if (ordinal < 1 || !List.of("REFERENCED", "UNREFERENCED", "UNMEASURABLE").contains(state)) {
                throw new IllegalArgumentException("Coverage hunk is invalid");
            }
            categoryIds = List.copyOf(Objects.requireNonNull(categoryIds, "categoryIds"));
            if (categoryIds.size() > 3 || categoryIds.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("Coverage categories are invalid");
            }
        }

        public EvidenceLocationView location() {
            return new EvidenceLocationView(relativePath, startLine, endLine);
        }
    }
}
