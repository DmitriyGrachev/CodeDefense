package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeDefenseToolWindowFactoryTest {
    @TempDir Path directory;

    @Test
    void derivesBundledCliFromInstalledPluginJarLocation() {
        Path pluginRoot = directory.resolve("CodeDefense");
        Path pluginJar = pluginRoot.resolve("lib").resolve("codedefense-jetbrains-0.1.0.jar");

        assertEquals(pluginRoot.resolve("cli").resolve("codedefense.jar"),
                CodeDefenseToolWindowFactory.bundledCliPath(pluginJar));
        assertNull(CodeDefenseToolWindowFactory.bundledCliPath(
                directory.resolve("unexpected").resolve("plugin.jar")));
    }
}
