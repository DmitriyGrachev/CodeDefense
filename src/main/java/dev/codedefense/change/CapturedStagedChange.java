package dev.codedefense.change;

import dev.codedefense.domain.StagedChange;
import java.util.List;
import java.util.Objects;

/** Safe transfer object for later bounded staged-context construction. */
public record CapturedStagedChange(
        StagedChange change,
        List<StagedHunk> hunks) {
    public CapturedStagedChange {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(hunks, "hunks");
        if (hunks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("hunks cannot contain null");
        }
        hunks = List.copyOf(hunks);
    }

    @Override
    public String toString() {
        return "CapturedStagedChange[change=%s, hunkCount=%d]".formatted(change, hunks.size());
    }
}
