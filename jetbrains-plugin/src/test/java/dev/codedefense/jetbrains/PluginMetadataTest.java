package dev.codedefense.jetbrains;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginMetadataTest {
    @Test
    void targetsOnlyIntellijIdeaBuild261AndRegistersLazyProjectToolWindow() throws IOException {
        String xml = resource("META-INF/plugin.xml");

        assertTrue(xml.contains("<id>dev.codedefense.jetbrains</id>"));
        assertTrue(xml.contains("<depends>com.intellij.modules.platform</depends>"));
        assertTrue(xml.contains("since-build=\"261\""));
        assertTrue(xml.contains("until-build=\"261.*\""));
        assertTrue(xml.contains("factoryClass=\"dev.codedefense.jetbrains.ui.CodeDefenseToolWindowFactory\""));
        assertFalse(xml.contains("com.intellij.modules.java"));
        assertFalse(xml.contains("com.intellij.modules.vcs"));
    }

    @Test
    void pinsApprovedBuildToolchainAndKeepsCliJarOffPluginClasspath() throws IOException {
        String build = Files.readString(Path.of("build.gradle.kts"), StandardCharsets.UTF_8);
        String wrapper = Files.readString(Path.of("gradle/wrapper/gradle-wrapper.properties"),
                StandardCharsets.UTF_8);

        assertTrue(build.contains("version \"2.18.1\""));
        assertTrue(build.contains("intellijIdea(\"2026.1.4\")"));
        assertTrue(build.contains("JavaLanguageVersion.of(21)"));
        assertTrue(build.contains("into(\"${project.name}/cli\")"));
        assertTrue(build.contains("buildSearchableOptions = false"));
        assertFalse(build.contains("implementation(files(coreJar"));
        assertTrue(wrapper.contains("gradle-9.0.0-bin.zip"));
    }

    private String resource(String name) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing test resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
