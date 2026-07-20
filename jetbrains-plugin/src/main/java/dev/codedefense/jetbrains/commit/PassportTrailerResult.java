package dev.codedefense.jetbrains.commit;

import java.util.Objects;

public record PassportTrailerResult(Status status, String commitMessage) {
    public PassportTrailerResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(commitMessage, "commitMessage");
    }

    public enum Status {
        ADDED,
        ALREADY_PRESENT,
        CONFLICT
    }
}
