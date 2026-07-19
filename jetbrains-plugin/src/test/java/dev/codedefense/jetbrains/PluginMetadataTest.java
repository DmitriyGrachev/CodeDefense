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
    void targetsIntellijIdeaBuilds261And262AndRegistersLazyProjectToolWindow() throws IOException {
        String xml = resource("META-INF/plugin.xml");

        assertTrue(xml.contains("<id>dev.codedefense.jetbrains</id>"));
        assertTrue(xml.contains("<depends>com.intellij.modules.platform</depends>"));
        assertTrue(xml.contains("<depends>Git4Idea</depends>"));
        assertTrue(xml.contains("serviceImplementation=\"dev.codedefense.jetbrains.gate.CodeDefenseProjectGateService\""));
        assertTrue(xml.contains("preload=\"true\""));
        assertTrue(xml.contains("since-build=\"261\""));
        assertTrue(xml.contains("until-build=\"262.*\""));
        assertTrue(xml.contains("factoryClass=\"dev.codedefense.jetbrains.ui.CodeDefenseToolWindowFactory\""));
        assertTrue(xml.contains("<checkinHandlerFactory implementation=\"dev.codedefense.jetbrains.gate.CodeDefenseCheckinHandlerFactory\""));
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
        assertTrue(build.contains("untilBuild = \"262.*\""));
        assertTrue(build.contains("JavaLanguageVersion.of(21)"));
        assertTrue(build.contains("into(\"${project.name}/cli\")"));
        assertTrue(build.contains("buildSearchableOptions = false"));
        assertTrue(build.contains("bundledPlugin(\"Git4Idea\")"));
        assertFalse(build.contains("implementation(files(coreJar"));
        assertTrue(wrapper.contains("gradle-9.0.0-bin.zip"));
    }

    @Test
    void locatesBundledCliWithoutInternalPluginManagerApi() throws IOException {
        String factory = Files.readString(Path.of(
                "src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.java"),
                StandardCharsets.UTF_8);
        String projectService = Files.readString(Path.of(
                "src/main/java/dev/codedefense/jetbrains/gate/CodeDefenseProjectGateService.java"),
                StandardCharsets.UTF_8);

        assertFalse(factory.contains("PluginManagerCore"));
        assertFalse(factory.contains("PluginManager.getPluginByClass("));
        assertFalse(factory.contains("getProtectionDomain()"));
        assertTrue(projectService.contains("JarURLConnection"));
    }

    @Test
    void advisoryCommitGateUsesOnlyApprovedPublicBoundaries() throws IOException {
        String handler = Files.readString(Path.of(
                "src/main/java/dev/codedefense/jetbrains/gate/CodeDefenseCheckinHandler.java"),
                StandardCharsets.UTF_8);
        String detector = Files.readString(Path.of(
                "src/main/java/dev/codedefense/jetbrains/gate/CommitModeDetector.java"),
                StandardCharsets.UTF_8);

        assertTrue(handler.contains("ProgressManager"));
        assertTrue(handler.contains("CodeDefenseProjectGateService"));
        assertTrue(handler.contains(".fresh("));
        assertFalse(handler.contains(".cached("));
        assertFalse(handler.contains("ProcessBuilder"));
        assertFalse(handler.contains("codex"));
        assertFalse(handler.contains("git "));
        assertFalse(handler.toLowerCase().contains("trailer"));
        assertFalse(handler.contains("setCommitMessage"));
        assertTrue(detector.contains("CheckinProjectPanel"));
        assertTrue(detector.contains("CommitContext"));
        assertTrue(detector.contains("GitStageCommitWorkflowHandler"));
        assertFalse(detector.contains(".impl."));
        assertFalse(detector.contains(".internal."));
    }

    private String resource(String name) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing test resource: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
