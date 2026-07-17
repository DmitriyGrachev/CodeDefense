package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReportConfigTest {
    @Test
    void providesExactSafeDefaults() {
        ReportConfig config = ReportConfig.defaults();

        assertEquals(Duration.ofSeconds(120), config.narrativeTimeout());
        assertEquals(1024 * 1024, config.maximumReportBytes());
        assertEquals(4096, config.maximumLatestPointerBytes());
        assertEquals(60, config.maximumProjectSlugLength());
    }

    @Test
    void rejectsOutOfRangeLimits() {
        assertThrows(IllegalArgumentException.class, () -> new ReportConfig(Duration.ZERO, 1024 * 1024, 4096, 60));
        assertThrows(IllegalArgumentException.class, () -> new ReportConfig(Duration.ofSeconds(1), 64 * 1024 - 1, 4096, 60));
        assertThrows(IllegalArgumentException.class, () -> new ReportConfig(Duration.ofSeconds(1), 64 * 1024, 255, 60));
        assertThrows(IllegalArgumentException.class, () -> new ReportConfig(Duration.ofSeconds(1), 64 * 1024, 256, 15));
        assertThrows(IllegalArgumentException.class, () -> new ReportConfig(Duration.ofSeconds(1), 64 * 1024, 256, 121));
    }
}
