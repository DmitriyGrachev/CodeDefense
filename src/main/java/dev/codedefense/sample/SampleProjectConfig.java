package dev.codedefense.sample;

import java.util.Objects;

/** Immutable resource and extraction limits for the embedded sample project. */
public record SampleProjectConfig(
        String resourcePath,
        int maximumArchiveBytes,
        int maximumEntries,
        int maximumEntryBytes,
        int maximumExpandedBytes,
        int maximumEntryPathCharacters) {
    public static final String DEFAULT_RESOURCE_PATH = "sample/sample-project.zip";
    public static final int REQUIRED_FILE_COUNT = 15;

    public SampleProjectConfig {
        Objects.requireNonNull(resourcePath, "Resource path");
        if (resourcePath.isBlank() || resourcePath.startsWith("/") || resourcePath.startsWith("\\")
                || resourcePath.contains("\\") || isDriveQualified(resourcePath)
                || hasUnsafePathSegment(resourcePath)) {
            throw new IllegalArgumentException("Resource path must be a relative classpath resource");
        }
        if (maximumArchiveBytes <= 0 || maximumEntries <= 0 || maximumEntryBytes <= 0
                || maximumExpandedBytes <= 0 || maximumEntryPathCharacters <= 0) {
            throw new IllegalArgumentException("Sample extraction bounds must be positive");
        }
        if (maximumExpandedBytes < maximumEntryBytes) {
            throw new IllegalArgumentException("Expanded archive bound cannot be smaller than one entry bound");
        }
        if (maximumEntries < REQUIRED_FILE_COUNT) {
            throw new IllegalArgumentException("Entry bound cannot be smaller than the required sample file count");
        }
    }

    public static SampleProjectConfig defaults() {
        return new SampleProjectConfig(DEFAULT_RESOURCE_PATH, 512 * 1024, 32,
                128 * 1024, 1024 * 1024, 240);
    }

    private static boolean isDriveQualified(String value) {
        return value.length() >= 2 && Character.isLetter(value.charAt(0)) && value.charAt(1) == ':';
    }

    private static boolean hasUnsafePathSegment(String value) {
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }
}
