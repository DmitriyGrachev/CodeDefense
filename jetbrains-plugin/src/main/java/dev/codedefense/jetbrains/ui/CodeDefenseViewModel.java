package dev.codedefense.jetbrains.ui;

import java.util.Objects;
import java.util.Optional;

public record CodeDefenseViewModel(boolean sessionActive, String status, Optional<String> passportPath) {
    public CodeDefenseViewModel {
        Objects.requireNonNull(status, "status");
        passportPath = Objects.requireNonNull(passportPath, "passportPath");
    }

    public static CodeDefenseViewModel initial() {
        return new CodeDefenseViewModel(false, "No Passport", Optional.empty());
    }
}
