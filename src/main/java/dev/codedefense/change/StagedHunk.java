package dev.codedefense.change;

import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.SourceLineRange;
import java.util.ArrayList;
import java.util.List;
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
        boolean truncated,
        List<SourceLineRange> changedNewLineRanges) {
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
        Objects.requireNonNull(changedNewLineRanges, "changedNewLineRanges");
        changedNewLineRanges = List.copyOf(changedNewLineRanges);
        if (changedNewLineRanges.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("changedNewLineRanges contain null");
        }
        int newEnd = newLineCount == 0 ? 0 : newStartLine + newLineCount - 1;
        if (changedNewLineRanges.stream().anyMatch(range -> newLineCount == 0
                || range.startLine() < newStartLine || range.endLine() > newEnd)) {
            throw new IllegalArgumentException("changedNewLineRanges escape the hunk");
        }
    }

    public StagedHunk(StagedChangeFile file, int oldStartLine, int oldLineCount,
            int newStartLine, int newLineCount, String unifiedContent, boolean truncated) {
        this(file, oldStartLine, oldLineCount, newStartLine, newLineCount,
                unifiedContent, truncated, inferChangedRanges(newStartLine, unifiedContent));
    }

    @Override
    public String toString() {
        return "StagedHunk[path=%s, old=%d-%d, new=%d-%d, truncated=%s]".formatted(
                file.path(), oldStartLine, oldLineCount, newStartLine, newLineCount, truncated);
    }

    private static List<SourceLineRange> inferChangedRanges(int newStartLine, String content) {
        Objects.requireNonNull(content, "unifiedContent");
        List<SourceLineRange> ranges = new ArrayList<>();
        int currentNewLine = newStartLine;
        int rangeStart = -1;
        for (String line : content.split("\\n", -1)) {
            if (line.isEmpty()) continue;
            char prefix = line.charAt(0);
            if (prefix == '+') {
                if (rangeStart < 0) rangeStart = currentNewLine;
                currentNewLine++;
            } else {
                if (rangeStart >= 0) {
                    ranges.add(new SourceLineRange(rangeStart, currentNewLine - 1));
                    rangeStart = -1;
                }
                if (prefix == ' ') currentNewLine++;
            }
        }
        if (rangeStart >= 0) ranges.add(new SourceLineRange(rangeStart, currentNewLine - 1));
        return List.copyOf(ranges);
    }
}
