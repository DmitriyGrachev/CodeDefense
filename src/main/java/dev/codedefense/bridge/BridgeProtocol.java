package dev.codedefense.bridge;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Stable limits and validation shared by the local IDE bridge contract. */
public final class BridgeProtocol {
    public static final int VERSION = 1;
    public static final int MAX_LINE_BYTES = 256 * 1024;
    public static final int MAX_ANSWER_BYTES = 8 * 1024;
    public static final int MAX_CAPABILITIES = 16;

    private BridgeProtocol() {
    }

    static void requireVersion(int version) {
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported bridge protocol version");
        }
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
}
