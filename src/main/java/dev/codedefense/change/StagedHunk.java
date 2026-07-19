package dev.codedefense.change;

import dev.codedefense.domain.StagedChangeFile;
import java.util.Objects;

/**
 * A bounded, parsed unified-diff hunk associated with a previously validated staged path.
 *
 * <p>The hunk text is source content and must not be exposed through {@link #toString()}.</p>
 */
public record StagedHunk(
        StagedChangeFile file,
        int oldStartLine,
        int oldLineCount,
        int newStartLine,
        int newLineCount,
        String unifiedContent,
        boolean truncated) {
    public StagedHunk {
        Objects.requireNonNull(file, "file");
        if (oldStartLine < 0 || newStartLine < 0 || oldLineCount < 0 || newLineCount < 0
                || (oldStartLine == 0 && oldLineCount != 0) || (newStartLine == 0 && newLineCount != 0)) {
            throw new IllegalArgumentException("Hunk line ranges are invalid");
        }
        unifiedContent = Objects.requireNonNull(unifiedContent, "unifiedContent");
        if (unifiedContent.isBlank()) {
            throw new IllegalArgumentException("unifiedContent must be nonblank");
        }
    }

    @Override
    public String toString() {
        return "StagedHunk[path=%s, old=%d-%d, new=%d-%d, truncated=%s]".formatted(
                file.path(), oldStartLine, oldLineCount, newStartLine, newLineCount, truncated);
    }
}
