package dev.codedefense.report;

import java.time.Duration;
import java.util.Objects;

public record ReportConfig(Duration narrativeTimeout, int maximumReportBytes, int maximumLatestPointerBytes,
                           int maximumProjectSlugLength) {
    public static final int DEFAULT_MAXIMUM_REPORT_BYTES = 1024 * 1024;
    public static final int DEFAULT_MAXIMUM_LATEST_POINTER_BYTES = 4096;
    public static final int DEFAULT_MAXIMUM_PROJECT_SLUG_LENGTH = 60;
    public static final Duration DEFAULT_NARRATIVE_TIMEOUT = Duration.ofSeconds(120);

    public ReportConfig {
        Objects.requireNonNull(narrativeTimeout, "narrativeTimeout");
        if (narrativeTimeout.isNegative() || narrativeTimeout.isZero()
                || maximumReportBytes < 64 * 1024 || maximumLatestPointerBytes < 256
                || maximumProjectSlugLength < 16 || maximumProjectSlugLength > 120) {
            throw new IllegalArgumentException("Report configuration is outside safe bounds");
        }
    }

    public static ReportConfig defaults() {
        return new ReportConfig(DEFAULT_NARRATIVE_TIMEOUT, DEFAULT_MAXIMUM_REPORT_BYTES,
                DEFAULT_MAXIMUM_LATEST_POINTER_BYTES, DEFAULT_MAXIMUM_PROJECT_SLUG_LENGTH);
    }
}
