package dev.codedefense.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Removes credentials from the environment inherited by a Codex process.
 */
public final class CodexProcessEnvironment {
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "TOKEN",
            "SECRET",
            "PASSWORD",
            "PASSWD",
            "API_KEY",
            "PRIVATE_KEY",
            "CREDENTIAL",
            "CONNECTION_STRING",
            "DATABASE_URL");

    private static final Set<String> EXPLICITLY_SENSITIVE = Set.of(
            "OPENAI_API_KEY",
            "CODEX_API_KEY",
            "CODEX_ACCESS_TOKEN",
            "GITHUB_TOKEN",
            "GH_TOKEN",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_SESSION_TOKEN");

    /**
     * Returns an immutable copy of {@code source} without sensitive entries.
     */
    public Map<String, String> sanitize(Map<String, String> source) {
        Objects.requireNonNull(source, "Source environment");
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "Environment key");
            String value = Objects.requireNonNull(entry.getValue(), "Environment value");
            if (!isSensitive(key)) {
                sanitized.put(key, value);
            }
        }
        return Map.copyOf(sanitized);
    }

    @Override
    public String toString() {
        return "CodexProcessEnvironment[sanitizesSensitiveKeys=true]";
    }

    private static boolean isSensitive(String key) {
        String normalized = key.toUpperCase(Locale.ROOT);
        return EXPLICITLY_SENSITIVE.contains(normalized)
                || SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }
}
