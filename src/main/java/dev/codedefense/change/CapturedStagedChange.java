package dev.codedefense.change;

import dev.codedefense.domain.StagedChange;
import java.util.List;
import java.util.Objects;

/** Safe transfer object for later bounded staged-context construction. */
public record CapturedStagedChange(StagedChange change, List<IndexBlob> blobs) {
    public CapturedStagedChange {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(blobs, "blobs");
        if (blobs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("blobs cannot contain null");
        }
        blobs = List.copyOf(blobs);
    }

    @Override
    public String toString() {
        return "CapturedStagedChange[change=%s, blobCount=%d]".formatted(change, blobs.size());
    }
}
