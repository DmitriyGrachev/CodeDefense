package dev.codedefense.domain;

/** An inclusive range of original source lines retained in a snapshot. */
public record SourceLineRange(int startLine, int endLine) {
    public SourceLineRange {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Source line range is invalid");
        }
    }

    public boolean contains(int start, int end) {
        return start >= startLine && end <= endLine;
    }
}
