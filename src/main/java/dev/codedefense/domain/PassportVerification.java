package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.Objects;

public record PassportVerification(Path passport, PassportStatus status) {
    public PassportVerification {
        Objects.requireNonNull(passport, "passport");
        Objects.requireNonNull(status, "status");
    }
}
