package dev.codedefense.change;

import dev.codedefense.domain.StagedChange;
import java.util.List;
import java.util.Objects;

/** Safe transfer object for later bounded staged-context construction. */
public record CapturedStagedChange(StagedChange change, List<IndexBlob> blobs, String canonicalDiff) {
    public CapturedStagedChange {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(blobs, "blobs");
        if (blobs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("blobs cannot contain null");
        }
        blobs = List.copyOf(blobs);
        Objects.requireNonNull(canonicalDiff, "canonicalDiff");
    }

    @Override
    public String toString() {
        return "CapturedStagedChange[change=%s, blobCount=%d, canonicalDiffBytes=%d]"
                .formatted(change, blobs.size(), canonicalDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }
}
