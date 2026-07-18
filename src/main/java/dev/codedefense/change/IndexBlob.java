package dev.codedefense.change;

import dev.codedefense.domain.StagedChangeFile;
import java.util.Objects;
import java.util.Optional;

/** Bounded index and base blob content for one safe changed path. */
public record IndexBlob(
        StagedChangeFile file,
        Optional<String> indexContent,
        boolean indexTruncated,
        Optional<String> baseContent,
        boolean baseTruncated) {
    public IndexBlob {
        Objects.requireNonNull(file, "file");
        indexContent = Objects.requireNonNull(indexContent, "indexContent");
        baseContent = Objects.requireNonNull(baseContent, "baseContent");
        if (indexTruncated && indexContent.isEmpty()) {
            throw new IllegalArgumentException("A truncated index blob must have content");
        }
        if (baseTruncated && baseContent.isEmpty()) {
            throw new IllegalArgumentException("A truncated base blob must have content");
        }
    }

    @Override
    public String toString() {
        return "IndexBlob[path=%s, status=%s, indexPresent=%s, indexTruncated=%s, basePresent=%s, baseTruncated=%s]"
                .formatted(file.path(), file.status(), indexContent.isPresent(), indexTruncated,
                        baseContent.isPresent(), baseTruncated);
    }
}
