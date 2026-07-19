package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeDefenseToolWindowFactoryTest {
    @TempDir Path directory;

    @Test
    void derivesBundledCliFromPluginClassResource() throws Exception {
        Path pluginRoot = directory.resolve("CodeDefense");
        Path pluginJar = pluginRoot.resolve("lib").resolve("codedefense-jetbrains-0.1.0.jar");
        var classResource = URI.create("jar:" + pluginJar.toUri()
                + "!/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.class").toURL();

        assertEquals(pluginRoot.resolve("cli").resolve("codedefense.jar"),
                CodeDefenseToolWindowFactory.bundledCliPath(classResource));
    }
}
