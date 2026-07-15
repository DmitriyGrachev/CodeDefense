package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void resolvesTheNpmPowerShellShimInsteadOfCmdOrExtensionlessShims() throws Exception {
        Path npmDirectory = Files.createDirectories(temporaryDirectory.resolve("npm"));
        Files.writeString(npmDirectory.resolve("codex"), "unix shim");
        Files.writeString(npmDirectory.resolve("codex.cmd"), "windows cmd shim");
        Path powerShellShim = Files.writeString(npmDirectory.resolve("codex.ps1"), "powershell shim");
        Path systemRoot = temporaryDirectory.resolve("Windows");

        CodexExecutableResolver resolver = new CodexExecutableResolver(
                "Windows 11",
                Map.of("PATH", npmDirectory.toString(), "SystemRoot", systemRoot.toString()));

        assertEquals(
                List.of(new CodexExecutable(List.of(
                        systemRoot.resolve("System32/WindowsPowerShell/v1.0/powershell.exe").toString(),
                        "-NoLogo",
                        "-NoProfile",
                        "-NonInteractive",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-File",
                        powerShellShim.toString()))),
                resolver.resolveCandidates());
    }
}
