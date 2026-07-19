package dev.codedefense.ai;

import java.util.Objects;

public record AppServerClientInfo(String name, String title, String version) {
    public AppServerClientInfo {
        name = require(name, "name");
        title = require(title, "title");
        version = require(version, "version");
    }

    private static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must be nonblank");
        return value;
    }
}
