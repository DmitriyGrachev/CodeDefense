package dev.codedefense.ai;

import java.util.Objects;

public record AppServerFileChange(String itemId, String path, String kind, String patch) {
    public AppServerFileChange {
        itemId = require(itemId, "item id");
        path = require(path, "file path");
        kind = require(kind, "change kind");
        patch = Objects.requireNonNull(patch, "patch");
        if (patch.length() > 1024 * 1024) throw new IllegalArgumentException("patch exceeds bound");
    }
    private static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must be nonblank");
        return value;
    }
    @Override public String toString() {
        return "AppServerFileChange[kind=%s, patchLength=%d]".formatted(kind, patch.length());
    }
}
