package dev.codedefense.ci;

import java.util.Objects;

public record GitRangeCommit(String commitId, String parentId, String message) {
    public GitRangeCommit {
        commitId = objectId(commitId, "commitId");
        parentId = objectId(parentId, "parentId");
        Objects.requireNonNull(message, "message");
    }

    private static String objectId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
}
