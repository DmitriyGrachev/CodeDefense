package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexExecutableResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesNativeExecutableFromWindowsPathWithoutRequiringSystemRoot() throws Exception {
        Path nativeDirectory = Files.createDirectories(temporaryDirectory.resolve("native"));
        Path nativeExecutable = Files.writeString(nativeDirectory.resolve("codex.exe"), "native executable");

        CodexExecutableResolver resolver = windowsResolver(Map.of("PATH", nativeDirectory.toString()));

        assertEquals(List.of(nativePrefix(nativeExecutable)), resolver.resolveCandidates());
    }

    @Test
    void resolvesTheNpmPowerShellShimInsteadOfCmdOrExtensionlessShims() throws Exception {
        Path npmDirectory = Files.createDirectories(temporaryDirectory.resolve("npm"));
        Files.writeString(npmDirectory.resolve("codex"), "unix shim");
        Files.writeString(npmDirectory.resolve("codex.cmd"), "windows cmd shim");
        Path powerShellShim = Files.writeString(npmDirectory.resolve("codex.ps1"), "powershell shim");
        Path powerShell = createPowerShell(temporaryDirectory.resolve("Windows"));

        CodexExecutableResolver resolver = windowsResolver(Map.of(
                "PATH", npmDirectory.toString(),
                "SystemRoot", temporaryDirectory.resolve("Windows").toString()));

        assertEquals(List.of(powerShellPrefix(powerShell, powerShellShim)), resolver.resolveCandidates());
    }

    @Test
    void prefersNativeExecutableBeforeNpmPowerShellShim() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("both"));
        Path nativeExecutable = Files.writeString(directory.resolve("codex.exe"), "native executable");
        Path powerShellShim = Files.writeString(directory.resolve("codex.ps1"), "powershell shim");
        Path powerShell = createPowerShell(temporaryDirectory.resolve("Windows"));

        CodexExecutableResolver resolver = windowsResolver(Map.of(
                "PATH", directory.toString(),
                "SystemRoot", temporaryDirectory.resolve("Windows").toString()));

        assertEquals(
                List.of(nativePrefix(nativeExecutable), powerShellPrefix(powerShell, powerShellShim)),
                resolver.resolveCandidates());
    }

    @Test
    void ignoresCmdOnlyWindowsInstallation() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("cmd-only"));
        Files.writeString(directory.resolve("codex.cmd"), "cmd shim");

        assertEquals(List.of(), windowsResolver(Map.of("PATH", directory.toString())).resolveCandidates());
    }

    @Test
    void resolvesQuotedPathEntry() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("quoted path"));
        Path nativeExecutable = Files.writeString(directory.resolve("codex.exe"), "native executable");

        CodexExecutableResolver resolver = windowsResolver(Map.of("PATH", '"' + directory.toString() + '"'));

        assertEquals(List.of(nativePrefix(nativeExecutable)), resolver.resolveCandidates());
    }

    @Test
    void keepsPathContainingSpacesAsOnePowerShellShimToken() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("npm with spaces"));
        Path powerShellShim = Files.writeString(directory.resolve("codex.ps1"), "powershell shim");
        Path powerShell = createPowerShell(temporaryDirectory.resolve("Windows"));

        CodexExecutableResolver resolver = windowsResolver(Map.of(
                "PATH", directory.toString(),
                "SystemRoot", temporaryDirectory.resolve("Windows").toString()));

        CodexExecutable executable = resolver.resolveCandidates().getFirst();
        assertEquals(powerShellPrefix(powerShell, powerShellShim), executable);
        assertEquals(powerShellShim.toAbsolutePath().normalize().toString(), executable.commandPrefix().getLast());
    }

    @Test
    void ignoresNpmShimWhenWindowsPowerShellDoesNotExist() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("npm"));
        Files.writeString(directory.resolve("codex.ps1"), "powershell shim");
        Path missingSystemRoot = temporaryDirectory.resolve("missing-windows");

        CodexExecutableResolver resolver = windowsResolver(Map.of(
                "PATH", directory.toString(), "SystemRoot", missingSystemRoot.toString()));

        assertEquals(List.of(), resolver.resolveCandidates());
    }

    @Test
    void fallsBackToWindirWhenSystemRootIsAbsent() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("npm"));
        Path powerShellShim = Files.writeString(directory.resolve("codex.ps1"), "powershell shim");
        Path windir = temporaryDirectory.resolve("Windows");
        Path powerShell = createPowerShell(windir);

        CodexExecutableResolver resolver = windowsResolver(Map.of(
                "PATH", directory.toString(), "WINDIR", windir.toString()));

        assertEquals(List.of(powerShellPrefix(powerShell, powerShellShim)), resolver.resolveCandidates());
    }

    @Test
    void doesNotTreatDarwinAsWindows() {
        assertEquals(
                List.of(new CodexExecutable(List.of("codex"))),
                new CodexExecutableResolver("Darwin", Map.of()).resolveCandidates());
    }

    @Test
    void keepsLinuxNativePathBehavior() {
        assertEquals(
                List.of(new CodexExecutable(List.of("codex"))),
                new CodexExecutableResolver("Linux", Map.of()).resolveCandidates());
    }

    @Test
    void deduplicatesIdenticalNativePrefixes() throws Exception {
        Path directory = Files.createDirectories(temporaryDirectory.resolve("native"));
        Path nativeExecutable = Files.writeString(directory.resolve("codex.exe"), "native executable");

        CodexExecutableResolver resolver = windowsResolver(Map.of(
                "PATH", joinPathEntries(directory, directory)));

        assertEquals(List.of(nativePrefix(nativeExecutable)), resolver.resolveCandidates());
    }

    private CodexExecutableResolver windowsResolver(Map<String, String> environment) {
        return new CodexExecutableResolver("Windows 11", environment);
    }

    private Path createPowerShell(Path windowsDirectory) throws Exception {
        Path powerShellDirectory = Files.createDirectories(windowsDirectory
                .resolve("System32")
                .resolve("WindowsPowerShell")
                .resolve("v1.0"));
        return Files.writeString(powerShellDirectory.resolve("powershell.exe"), "powershell executable");
    }

    private static CodexExecutable nativePrefix(Path nativeExecutable) {
        return new CodexExecutable(List.of(nativeExecutable.toAbsolutePath().normalize().toString()));
    }

    private static CodexExecutable powerShellPrefix(Path powerShell, Path powerShellShim) {
        return new CodexExecutable(List.of(
                powerShell.toAbsolutePath().normalize().toString(),
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                powerShellShim.toAbsolutePath().normalize().toString()));
    }

    private static String joinPathEntries(Path... entries) {
        return java.util.Arrays.stream(entries)
                .map(Path::toString)
                .collect(java.util.stream.Collectors.joining(File.pathSeparator));
    }
}
