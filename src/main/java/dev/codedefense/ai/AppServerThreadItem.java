package dev.codedefense.ai;

import java.util.List;
import java.util.Objects;

public record AppServerThreadItem(String id, String type, List<AppServerFileChange> fileChanges) {
    public AppServerThreadItem {
        id = require(id, "item id");
        type = require(type, "item type");
        Objects.requireNonNull(fileChanges, "fileChanges");
        if (fileChanges.size() > 100 || fileChanges.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("file change bound exceeded");
        }
        fileChanges = List.copyOf(fileChanges);
    }
    private static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must be nonblank");
        return value;
    }
    @Override public String toString() {
        return "AppServerThreadItem[type=%s, fileChangeCount=%d]".formatted(type, fileChanges.size());
    }
}
