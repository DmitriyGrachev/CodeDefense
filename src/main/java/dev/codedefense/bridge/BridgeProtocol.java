package dev.codedefense.bridge;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Stable limits and validation shared by the local IDE bridge contract. */
public final class BridgeProtocol {
    public static final int VERSION_1 = 1;
    public static final int VERSION_2 = 2;
    public static final int CURRENT_VERSION = VERSION_2;
    /** Source-compatibility alias for the original bridge version. */
    public static final int VERSION = VERSION_1;
    public static final int MAX_LINE_BYTES = 256 * 1024;
    public static final int MAX_ANSWER_BYTES = 8 * 1024;
    public static final int MAX_CAPABILITIES = 16;

    private BridgeProtocol() {
    }

    public static int requireSupportedVersion(int version) {
        if (version != VERSION_1 && version != VERSION_2) {
            throw new IllegalArgumentException("Unsupported bridge protocol version");
        }
        return version;
    }

    static void requireVersion(int version) {
        requireSupportedVersion(version);
    }

    static String requireText(String value, String name, int maximumCharacters) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumCharacters) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    static List<String> copyStrings(List<String> values, String name, int maximumCount, int maximumCharacters) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximumCount) {
            throw new IllegalArgumentException(name + " has too many values");
        }
        List<String> copy = values.stream()
                .map(value -> requireText(value, name, maximumCharacters))
                .toList();
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(name + " contains duplicates");
        }
        return copy;
    }

    static int requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its supported range");
        }
        return value;
    }

    static String requireRelativePath(String value, String name, int maximumCharacters) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumCharacters || !value.equals(value.strip())
                || value.startsWith("/") || value.startsWith("\\")
                || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " is not a portable relative path");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(name + " is not a portable relative path");
            }
        }
        return value;
    }

    static List<BridgeEvidenceLocation> copyEvidence(int version, boolean followUp,
            List<BridgeEvidenceLocation> values) {
        requireSupportedVersion(version);
        Objects.requireNonNull(values, "evidence");
        if (values.stream().anyMatch(Objects::isNull) || values.size() > 10) {
            throw new IllegalArgumentException("evidence has an invalid size");
        }
        List<BridgeEvidenceLocation> copy = values.stream()
                .sorted(java.util.Comparator.comparing(BridgeEvidenceLocation::relativePath)
                        .thenComparingInt(BridgeEvidenceLocation::startLine)
                        .thenComparingInt(BridgeEvidenceLocation::endLine))
                .toList();
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("evidence contains duplicates");
        }
        if (version == VERSION_1 && !copy.isEmpty()) {
            throw new IllegalArgumentException("protocol 1 questions cannot contain evidence");
        }
        if (version == VERSION_2 && (followUp ? !copy.isEmpty() : copy.isEmpty())) {
            throw new IllegalArgumentException(followUp
                    ? "protocol 2 follow-ups cannot contain evidence"
                    : "protocol 2 primary questions require evidence");
        }
        return copy;
    }
}
