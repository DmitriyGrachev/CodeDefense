package dev.codedefense.ci;

import java.util.Locale;
import java.util.Objects;

public enum CiPassportPolicy {
    ADVISORY, REQUIRED;

    public int exitCode(PassportContinuityResult result) {
        Objects.requireNonNull(result, "result");
        if (result.unavailable()) return 10;
        return this == ADVISORY || result.allMatched() ? 0 : 1;
    }

    public static CiPassportPolicy parse(String value) {
        return valueOf(Objects.requireNonNull(value, "value").toUpperCase(Locale.ROOT));
    }
}
