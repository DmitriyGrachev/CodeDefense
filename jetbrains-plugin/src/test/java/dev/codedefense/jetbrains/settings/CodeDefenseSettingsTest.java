package dev.codedefense.jetbrains.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeDefenseSettingsTest {
    @TempDir Path temp;

    @Test
    void storesOnlyCliChoiceSelectorAndFocusAndCopiesState() {
        var settings = new CodeDefenseSettings();
        var state = new CodeDefenseSettings.State();
        state.useBundledCli = false;
        state.cliJarOverride = "C:/safe/codedefense.jar";
        state.defaultSelector = "COMMIT";
        state.defaultFocus = "testing";

        settings.loadState(state);
        state.cliJarOverride = "changed";

        assertFalse(settings.getState().useBundledCli);
        assertEquals("C:/safe/codedefense.jar", settings.getState().cliJarOverride);
        assertEquals("COMMIT", settings.getState().defaultSelector);
        assertEquals("testing", settings.getState().defaultFocus);
        assertFalse(settings.getState().toString().contains("answer"));
    }

    @Test
    void acceptsOnlyExistingRegularNonSymlinkJarOverride() throws Exception {
        Path jar = Files.createFile(temp.resolve("codedefense.jar"));
        assertEquals(jar.toRealPath(), CodeDefenseSettings.validateOverride(jar));
        assertThrows(IllegalArgumentException.class,
                () -> CodeDefenseSettings.validateOverride(temp.resolve("missing.jar")));
        assertThrows(IllegalArgumentException.class,
                () -> CodeDefenseSettings.validateOverride(Files.createFile(temp.resolve("not.txt"))));
        Path link = temp.resolve("link.jar");
        try {
            Files.createSymbolicLink(link, jar);
            assertThrows(IllegalArgumentException.class, () -> CodeDefenseSettings.validateOverride(link));
        } catch (UnsupportedOperationException | java.io.IOException exception) {
            assertTrue(Files.isRegularFile(jar));
        }
    }
}
