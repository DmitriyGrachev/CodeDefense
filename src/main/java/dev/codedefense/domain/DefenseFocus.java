package dev.codedefense.domain;

import java.util.Locale;

public enum DefenseFocus {
    BALANCED("balanced", "Balanced"),
    ARCHITECTURE("architecture", "Architecture"),
    FAILURE_MODES("failure-modes", "Failure modes"),
    TESTING("testing", "Testing");
    private final String cliName; private final String displayName;
    DefenseFocus(String cliName, String displayName) { this.cliName = cliName; this.displayName = displayName; }
    public String cliName() { return cliName; }
    public String displayName() { return displayName; }
    public static DefenseFocus parse(String value) {
        if (value != null) for (DefenseFocus focus : values())
            if (focus.cliName.equals(value.toLowerCase(Locale.ROOT))) return focus;
        throw new IllegalArgumentException("Unknown defense focus.");
    }
}
