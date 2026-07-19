package dev.codedefense.application;

import dev.codedefense.domain.PassportStatus;
import java.nio.file.Path;
import java.util.Objects;

public record SavedChangePassport(Path path, PassportStatus status) {
    public SavedChangePassport {
        Objects.requireNonNull(path, "path");
        path = path.toAbsolutePath().normalize();
        Objects.requireNonNull(status, "status");
    }
}
