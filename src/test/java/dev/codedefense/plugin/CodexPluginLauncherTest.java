package dev.codedefense.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexPluginLauncherTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path POWERSHELL = ROOT.resolve(
            "plugins/codedefense/scripts/codedefense-hook.ps1");
    private static final Path POSIX = ROOT.resolve(
            "plugins/codedefense/scripts/codedefense-hook.sh");
    private static final String UNAVAILABLE = "CodeDefense hook launcher is unavailable.";

    @TempDir
    Path temporaryDirectory;

    @Test
    void launcherSourcesUseOnlyFixedSafeInvocationForms() throws Exception {
        assertTrue(Files.isRegularFile(POWERSHELL));
        assertTrue(Files.isRegularFile(POSIX));
        String powerShell = Files.readString(POWERSHELL, StandardCharsets.UTF_8);
        String posix = Files.readString(POSIX, StandardCharsets.UTF_8);

        for (String forbidden : List.of("Invoke-Expression", "cmd.exe", "powershell.exe -Command",
                "Write-Output")) {
            assertFalse(powerShell.contains(forbidden));
        }
        assertFalse(posix.contains("eval"));
        assertTrue(powerShell.contains("& $Java -jar $Jar codex-hook status"));
        assertTrue(posix.contains("\"$java_command\" -jar \"$jar\" codex-hook status"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellLauncherPreservesArgumentsAcrossPathsWithSpaces() throws Exception {
        Path plugin = copyLauncher(POWERSHELL);
        createFixtureJar(plugin.resolve("cli/codedefense.jar"));

        Execution execution = executePowerShell(plugin, System.getProperty("java.home"), null);

        assertEquals(0, execution.exitCode());
        assertEquals("codex-hook|status", execution.stdout());
        assertEquals("", execution.stderr());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellLauncherRejectsMissingJarWithOneSafeDiagnostic() throws Exception {
        Path plugin = copyLauncher(POWERSHELL);

        Execution execution = executePowerShell(plugin, System.getProperty("java.home"), null);

        assertNotEquals(0, execution.exitCode());
        assertEquals("", execution.stdout());
        assertEquals(UNAVAILABLE, execution.stderr().strip());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void powershellLauncherRejectsMissingJavaWithOneSafeDiagnostic() throws Exception {
        Path plugin = copyLauncher(POWERSHELL);
        createFixtureJar(plugin.resolve("cli/codedefense.jar"));

        Execution execution = executePowerShell(plugin,
                temporaryDirectory.resolve("missing-java-home").toString(), "");

        assertNotEquals(0, execution.exitCode());
        assertEquals("", execution.stdout());
        assertEquals(UNAVAILABLE, execution.stderr().strip());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void posixLauncherPreservesArgumentsAcrossPathsWithSpaces() throws Exception {
        Path plugin = copyLauncher(POSIX);
        createFixtureJar(plugin.resolve("cli/codedefense.jar"));

        Execution execution = executePosix(plugin, System.getProperty("java.home"), null);

        assertEquals(0, execution.exitCode());
        assertEquals("codex-hook|status", execution.stdout());
        assertEquals("", execution.stderr());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void posixLauncherRejectsMissingJarWithOneSafeDiagnostic() throws Exception {
        Path plugin = copyLauncher(POSIX);

        Execution execution = executePosix(plugin, System.getProperty("java.home"), null);

        assertNotEquals(0, execution.exitCode());
        assertEquals("", execution.stdout());
        assertEquals(UNAVAILABLE, execution.stderr().strip());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void posixLauncherRejectsMissingJavaWithOneSafeDiagnostic() throws Exception {
        Path plugin = copyLauncher(POSIX);
        createFixtureJar(plugin.resolve("cli/codedefense.jar"));

        Execution execution = executePosix(plugin,
                temporaryDirectory.resolve("missing-java-home").toString(), "");

        assertNotEquals(0, execution.exitCode());
        assertEquals("", execution.stdout());
        assertEquals(UNAVAILABLE, execution.stderr().strip());
    }

    private Path copyLauncher(Path source) throws IOException {
        assertTrue(Files.isRegularFile(source));
        Path plugin = temporaryDirectory.resolve("plugin with spaces");
        Path scripts = Files.createDirectories(plugin.resolve("scripts"));
        Files.copy(source, scripts.resolve(source.getFileName()));
        return plugin;
    }

    private static Execution executePowerShell(Path plugin, String javaHome, String path) throws Exception {
        Path executable = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell",
                "v1.0", "powershell.exe");
        ProcessBuilder builder = new ProcessBuilder(executable.toString(), "-NoLogo", "-NoProfile",
                "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File",
                plugin.resolve("scripts/codedefense-hook.ps1").toString());
        return execute(builder, javaHome, path);
    }

    private static Execution executePosix(Path plugin, String javaHome, String path) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("/bin/sh",
                plugin.resolve("scripts/codedefense-hook.sh").toString());
        return execute(builder, javaHome, path);
    }

    private static Execution execute(ProcessBuilder builder, String javaHome, String path) throws Exception {
        builder.environment().put("JAVA_HOME", javaHome);
        if (path != null) {
            builder.environment().put("PATH", path);
        }
        Process process = builder.start();
        assertTrue(process.waitFor(15, TimeUnit.SECONDS));
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Execution(process.exitValue(), stdout, stderr);
    }

    private static void createFixtureJar(Path jar) throws IOException {
        Files.createDirectories(jar.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,
                LauncherFixtureMain.class.getName());
        String classResource = LauncherFixtureMain.class.getName().replace('.', '/') + ".class";
        try (InputStream input = LauncherFixtureMain.class.getClassLoader()
                .getResourceAsStream(classResource);
                JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            if (input == null) {
                throw new IOException("Fixture class resource is unavailable");
            }
            output.putNextEntry(new JarEntry(classResource));
            input.transferTo(output);
            output.closeEntry();
        }
    }

    public static final class LauncherFixtureMain {
        private LauncherFixtureMain() {
        }

        public static void main(String[] arguments) {
            System.out.print(String.join("|", arguments));
        }
    }

    private record Execution(int exitCode, String stdout, String stderr) {
    }
}
