package dev.codedefense.jetbrains.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeDefenseLauncherTest {
    @TempDir Path temp;

    @Test
    void resolvesJavaOnlyBelowConfiguredJavaHome() throws Exception {
        Path javaHome = Files.createDirectories(temp.resolve("runtime with spaces"));
        Path bin = Files.createDirectories(javaHome.resolve("bin"));
        Path executable = Files.createFile(bin.resolve(isWindows() ? "java.exe" : "java"));

        assertEquals(executable.toRealPath(LinkOption.NOFOLLOW_LINKS),
                new JavaExecutableResolver().resolve(javaHome));
        assertThrows(BridgeTransportException.class,
                () -> new JavaExecutableResolver().resolve(temp.resolve("missing")));
    }

    @Test
    void provenanceLaunchUsesOnlyFeatureFlagAndNeverThreadId() throws Exception {
        Path javaHome = Files.createDirectories(temp.resolve("runtime-provenance"));
        Files.createFile(Files.createDirectories(javaHome.resolve("bin"))
                .resolve(isWindows() ? "java.exe" : "java"));
        Path jar = Files.createFile(temp.resolve("codedefense.jar"));
        Path project = Files.createDirectories(temp.resolve("project"));
        var launcher = new JdkCodeDefenseLauncher(jar, javaHome);
        var spec = new CodeDefenseLauncher.BridgeLaunchSpec(
                CodeDefenseLauncher.Selector.STAGED, null, "balanced", true, true);
        List<String> command = launcher.command(project, spec);

        assertTrue(command.contains("--experimental-codex-provenance"));
        assertFalse(command.stream().anyMatch(token -> token.contains("private-thread-id")));
    }

    @Test
    void buildsExactTokensWithoutShellAndPreservesSpacesAndUnicode() throws Exception {
        Path javaHome = Files.createDirectories(temp.resolve("runtime"));
        Path bin = Files.createDirectories(javaHome.resolve("bin"));
        Path java = Files.createFile(bin.resolve(isWindows() ? "java.exe" : "java"));
        Path jar = Files.createFile(Files.createDirectories(temp.resolve("plugin home/cli"))
                .resolve("codedefense.jar"));
        Path project = Files.createDirectories(temp.resolve("Проект with spaces"));
        var launcher = new JdkCodeDefenseLauncher(jar, javaHome);
        var spec = new CodeDefenseLauncher.BridgeLaunchSpec(
                CodeDefenseLauncher.Selector.STAGED, null, "testing", true);

        List<String> command = launcher.command(project, spec);

        assertEquals(List.of(java.toRealPath(LinkOption.NOFOLLOW_LINKS).toString(), "-jar",
                jar.toRealPath(LinkOption.NOFOLLOW_LINKS).toString(), "bridge", "prove", "--protocol", "1",
                "--staged", "--focus", "testing", "--dry-run", project.toRealPath().toString()), command);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
