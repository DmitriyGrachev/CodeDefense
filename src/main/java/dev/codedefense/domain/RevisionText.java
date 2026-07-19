package dev.codedefense.domain;

import java.util.Objects;

final class RevisionText {
    private RevisionText() { }
    static String requireSafe(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > 256 || value.charAt(0) == '-'
                || value.contains("..") || value.chars().anyMatch(Character::isISOControl)
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(field + " is unsafe");
        }
        return value;
    }
}
