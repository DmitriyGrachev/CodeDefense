package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CodeDefensePathsTest {
    @Test
    void constructsNormalizedPathsWithoutIo() {
        Path home = Path.of("build", "path-test", "..", "home");
        CodeDefensePaths paths = CodeDefensePaths.under(home);

        assertEquals(home.toAbsolutePath().normalize().resolve(".codedefense"), paths.rootDirectory());
        assertEquals(paths.rootDirectory().resolve("reports"), paths.reportsDirectory());
        assertEquals(paths.rootDirectory().resolve("latest-report.txt"), paths.latestPointer());
        assertFalse(Files.exists(paths.rootDirectory()));
    }

    @Test
    void mapsBlankOrMissingUserHomeToSafePersistenceFailure() {
        String original = System.getProperty("user.home");
        try {
            System.setProperty("user.home", " ");
            assertThrows(ReportPersistenceException.class, CodeDefensePaths::defaults);
            System.clearProperty("user.home");
            assertThrows(ReportPersistenceException.class, CodeDefensePaths::defaults);
        } finally {
            if (original == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", original);
            }
        }
    }
}
