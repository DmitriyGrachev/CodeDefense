package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record EvidenceCoverageHunk(String relativePath, int ordinal, int startLine, int endLine,
        boolean navigable, EvidenceCoverageState state, List<String> categoryIds) {
    public EvidenceCoverageHunk {
        relativePath = requireRelativePath(relativePath);
        if (ordinal < 1 || startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Coverage hunk location is invalid");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(categoryIds, "categoryIds");
        categoryIds = List.copyOf(categoryIds);
        if (categoryIds.size() > 3 || categoryIds.stream().anyMatch(value -> value == null || value.isBlank())
                || new HashSet<>(categoryIds).size() != categoryIds.size()) {
            throw new IllegalArgumentException("Coverage categories are invalid");
        }
        if ((state == EvidenceCoverageState.REFERENCED) != !categoryIds.isEmpty()) {
            throw new IllegalArgumentException("Coverage state and categories disagree");
        }
    }

    private static String requireRelativePath(String value) {
        Objects.requireNonNull(value, "relativePath");
        if (value.isBlank() || value.startsWith("/") || value.startsWith("\\") || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Coverage path is unsafe");
        }
        Path path = Path.of(value);
        if (path.isAbsolute() || !path.equals(path.normalize())) {
            throw new IllegalArgumentException("Coverage path is unsafe");
        }
        for (Path segment : path) {
            if (segment.toString().equals("..") || segment.toString().equals(".")) {
                throw new IllegalArgumentException("Coverage path is unsafe");
            }
        }
        return value;
    }
}
