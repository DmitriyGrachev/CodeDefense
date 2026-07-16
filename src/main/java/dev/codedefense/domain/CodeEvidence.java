package dev.codedefense.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CodeEvidence(String path, int startLine, int endLine, String reason) {
    public CodeEvidence {
        path = requireNonBlank(path, "path");
        reason = requireNonBlank(reason, "reason");
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Evidence line range is invalid");
        }
    }

    static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank");
        }
        return value.strip();
    }

    static List<String> copyNonBlankStrings(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        List<String> copy = new ArrayList<>(values.size());
        for (String value : values) {
            copy.add(requireNonBlank(value, field + " item"));
        }
        return List.copyOf(copy);
    }
}
