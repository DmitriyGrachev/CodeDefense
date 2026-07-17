package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SavedReportTest {
    @Test
    void storesAnAbsoluteNormalizedPath() {
        Path path = Path.of("reports/report.md").toAbsolutePath().normalize();
        assertEquals(path, new SavedReport(path, NarrativeSource.DETERMINISTIC_FALLBACK).path());
        assertEquals(path, new SavedReport(path.resolve("folder/.."), NarrativeSource.AI).path());
        assertThrows(IllegalArgumentException.class, () -> new SavedReport(Path.of("reports/report.md"), NarrativeSource.AI));
    }
}
