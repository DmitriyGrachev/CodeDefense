package dev.codedefense.domain;

import java.util.Objects;

public record AnalyzedFile(String path, int includedLines, boolean truncated, int renderedBytes) {
    public AnalyzedFile {
        path = normalizePath(path);
        if (includedLines <= 0 || renderedBytes <= 0) {
            throw new IllegalArgumentException("Analyzed file sizes must be positive");
        }
    }

    static String normalizePath(String value) {
        Objects.requireNonNull(value, "path");
        String normalized = value.strip().replace('\\', '/').replaceAll("/+", "/");
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Path must be a portable relative path");
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Path must not contain empty, dot, or parent segments");
            }
        }
        return String.join("/", segments);
    }
}
