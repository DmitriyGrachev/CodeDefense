package dev.codedefense.ai;

import java.util.List;
import java.util.Objects;

public record AppServerThread(String id, String cwd, String sourceKind,
        List<AppServerThreadItem> items) {
    public AppServerThread {
        id = require(id, "thread id");
        cwd = require(cwd, "thread cwd");
        sourceKind = require(sourceKind, "thread source kind");
        Objects.requireNonNull(items, "items");
        if (items.size() > 1_000 || items.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("thread item bound exceeded");
        }
        items = List.copyOf(items);
    }

    private static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must be nonblank");
        return value;
    }

    @Override public String toString() {
        return "AppServerThread[sourceKind=%s, itemCount=%d]".formatted(sourceKind, items.size());
    }
}
