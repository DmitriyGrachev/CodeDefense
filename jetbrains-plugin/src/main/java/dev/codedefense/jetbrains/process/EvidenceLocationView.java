package dev.codedefense.jetbrains.process;

import java.util.Objects;

/** Source-free, portable evidence coordinates received from the local bridge. */
public record EvidenceLocationView(String relativePath, int startLine, int endLine) {
    private static final int MAXIMUM_PATH_CHARACTERS = 4096;

    public EvidenceLocationView {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank() || !relativePath.equals(relativePath.strip())
                || relativePath.length() > MAXIMUM_PATH_CHARACTERS
                || relativePath.startsWith("/") || relativePath.startsWith("//")
                || relativePath.indexOf('\\') >= 0 || relativePath.indexOf(':') >= 0
                || relativePath.chars().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        String[] segments = relativePath.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw invalid();
            }
        }
        if (startLine < 1 || endLine < startLine) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Evidence location is invalid.");
    }
}
