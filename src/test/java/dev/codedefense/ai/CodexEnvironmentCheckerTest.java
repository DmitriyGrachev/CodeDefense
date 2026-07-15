package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexEnvironmentCheckerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesFirstCandidateRunsLoginAndUsesSanitizedEnvironmentForEverySpec() {
        FakeProcessExecutor executor = new FakeProcessExecutor(
                result(0, "codex 1.2.3\n", ""),
                result(0, "login-output-must-not-leak", ""));
        Map<String, String> sourceEnvironment = sourceEnvironment();

        CodexEnvironment environment = checker(executor, sourceEnvironment, "Linux").checkReady();

        assertEquals(List.of("codex"), environment.executable().commandPrefix());
        assertEquals("codex 1.2.3", environment.version());
        assertCommands(executor.specifications(), List.of(
                List.of("codex", "--version"),
                List.of("codex", "login", "status")));
        for (ProcessSpec spec : executor.specifications()) {
            assertEquals("", spec.standardInput());
            assertEquals(CodexRuntimeConfig.defaults().environmentCheckTimeout(), spec.timeout());
            assertEquals(CodexRuntimeConfig.defaults().terminationGracePeriod(), spec.terminationGracePeriod());
            assertEquals(CodexRuntimeConfig.defaults().maximumCapturedStdoutBytes(), spec.maximumStdoutBytes());
            assertEquals(CodexRuntimeConfig.defaults().maximumCapturedStderrBytes(), spec.maximumStderrBytes());
            assertEquals(temporaryDirectory, spec.workingDirectory());
            assertEquals("ordinary", spec.environment().get("ORDINARY"));
            assertFalse(spec.environment().containsKey("OPENAI_API_KEY"));
        }
        assertEquals("openai-secret", sourceEnvironment.get("OPENAI_API_KEY"));
        assertFalse(environment.toString().contains("login-output-must-not-leak"));
    }

    @Test
    void usesTheResolvedWindowsNpmPowerShellShimForVersionAndLogin() throws Exception {
        Path npmDirectory = Files.createDirectories(temporaryDirectory.resolve("npm"));
        Files.writeString(npmDirectory.resolve("codex"), "unix shim");
        Files.writeString(npmDirectory.resolve("codex.cmd"), "cmd shim");
        Path powerShellShim = Files.writeString(npmDirectory.resolve("codex.ps1"), "powershell shim");
        Path systemRoot = temporaryDirectory.resolve("Windows");
        Path powerShell = createPowerShell(systemRoot);
        List<String> prefix = List.of(
                powerShell.toString(),
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                powerShellShim.toString());
        FakeProcessExecutor executor = new FakeProcessExecutor(result(0, "codex 2", ""), result(0, "", ""));

        CodexEnvironment environment = checker(
                        executor,
                        Map.of("PATH", npmDirectory.toString(), "SystemRoot", systemRoot.toString()),
                        "Windows 11")
                .checkReady();

        assertEquals(prefix, environment.executable().commandPrefix());
        assertCommands(executor.specifications(), List.of(
                append(prefix, "--version"),
                append(prefix, "login", "status")));
    }

    @Test
    void doesNotTreatDarwinAsWindows() {
        FakeProcessExecutor executor = new FakeProcessExecutor(result(0, "codex 2", ""), result(0, "", ""));

        checker(executor, Map.of(), "Darwin").checkReady();

        assertCommands(executor.specifications(), List.of(
                List.of("codex", "--version"),
                List.of("codex", "login", "status")));
    }

    @Test
    void reportsNotInstalledWhenEveryCandidateFailsToLaunch() {
        FakeProcessExecutor executor = new FakeProcessExecutor(missingExecutable(), missingExecutable(), missingExecutable());

        assertThrows(CodexNotInstalledException.class, () -> checker(executor, Map.of(), "Windows").checkReady());
    }

    @Test
    void reportsTimeoutWhenVersionCheckTimesOut() {
        FakeProcessExecutor executor = new FakeProcessExecutor(timeout());

        assertThrows(CodexTimeoutException.class, () -> checker(executor, Map.of(), "Linux").checkReady());
    }

    @Test
    void mapsANonzeroWindowsNpmShimVersionCheckToExecutionFailure() throws Exception {
        FakeProcessExecutor executor = new FakeProcessExecutor(result(17, "", "failure"));

        CodexExecutionException exception = assertThrows(
                CodexExecutionException.class,
                () -> checker(executor, windowsNpmEnvironment(), "Windows").checkReady());

        assertEquals(17, exception.exitCode());
    }

    @Test
    void reportsExecutionFailureWhenTheOnlyNonWindowsCandidateReturnsNonzero() {
        FakeProcessExecutor executor = new FakeProcessExecutor(result(3, "", "failure"));

        CodexExecutionException exception = assertThrows(
                CodexExecutionException.class, () -> checker(executor, Map.of(), "Linux").checkReady());

        assertEquals(3, exception.exitCode());
    }

    @Test
    void mapsNonzeroLoginStatusToNotAuthenticatedWithoutExposingOutput() {
        FakeProcessExecutor executor = new FakeProcessExecutor(
                result(0, "codex 1", ""), result(1, "auth secret output", "more auth secret output"));

        CodexNotAuthenticatedException exception = assertThrows(
                CodexNotAuthenticatedException.class, () -> checker(executor, Map.of(), "Linux").checkReady());

        assertFalse(exception.getMessage().contains("auth secret output"));
        assertFalse(exception.getMessage().contains("more auth secret output"));
    }

    @Test
    void mapsTimedOutLoginStatusToTimeout() {
        FakeProcessExecutor executor = new FakeProcessExecutor(result(0, "codex 1", ""), timeout());

        assertThrows(CodexTimeoutException.class, () -> checker(executor, Map.of(), "Linux").checkReady());
    }

    @Test
    void usesFirstNonblankStdoutLineForVersion() {
        CodexEnvironment environment = successfulEnvironment("\n  \nfirst version\nsecond version", "stderr version");

        assertEquals("first version", environment.version());
    }

    @Test
    void fallsBackToFirstNonblankStderrVersionLine() {
        CodexEnvironment environment = successfulEnvironment("  \n", "\nversion from stderr\n");

        assertEquals("version from stderr", environment.version());
    }

    @Test
    void normalizesLineEndingsAndRemovesControlCharactersFromVersion() {
        CodexEnvironment environment = successfulEnvironment("\r\nco\u0007dex 1.0\rnext", "");

        assertEquals("codex 1.0", environment.version());
    }

    @Test
    void capsVersionAt256Characters() {
        String version = "v".repeat(300);

        CodexEnvironment environment = successfulEnvironment(version, "");

        assertEquals(256, environment.version().length());
    }

    @Test
    void rejectsBlankVersionOutput() {
        FakeProcessExecutor executor = new FakeProcessExecutor(result(0, " \r\n\u0000", "\u0007"));

        assertThrows(CodexExecutionException.class, () -> checker(executor, Map.of(), "Linux").checkReady());
    }

    @Test
    void mapsUnrelatedIllegalStateExceptionToExecutionFailureRatherThanNotInstalled() {
        FakeProcessExecutor executor = new FakeProcessExecutor(new IllegalStateException("executor defect"));

        CodexExecutionException exception = assertThrows(
                CodexExecutionException.class, () -> checker(executor, Map.of(), "Linux").checkReady());

        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    void mapsAnInterruptedExecutorToTheTypedInterruptedFailure() {
        ProcessExecutor executor = specification -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        };

        try {
            assertThrows(CodexInterruptedException.class, () -> checker(executor, Map.of(), "Linux").checkReady());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private CodexEnvironment successfulEnvironment(String stdout, String stderr) {
        return checker(new FakeProcessExecutor(result(0, stdout, stderr), result(0, "", "")), Map.of(), "Linux")
                .checkReady();
    }

    private CodexEnvironmentChecker checker(ProcessExecutor executor, Map<String, String> environment, String operatingSystem) {
        return new CodexEnvironmentChecker(
                executor,
                CodexRuntimeConfig.defaults(),
                new CodexProcessEnvironment(),
                environment,
                temporaryDirectory,
                operatingSystem);
    }

    private static Map<String, String> sourceEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("OPENAI_API_KEY", "openai-secret");
        environment.put("ORDINARY", "ordinary");
        return environment;
    }

    private Map<String, String> windowsNpmEnvironment() throws Exception {
        Path npmDirectory = Files.createDirectories(temporaryDirectory.resolve("npm"));
        Files.writeString(npmDirectory.resolve("codex.ps1"), "powershell shim");
        Path systemRoot = temporaryDirectory.resolve("Windows");
        createPowerShell(systemRoot);
        return Map.of("PATH", npmDirectory.toString(), "SystemRoot", systemRoot.toString());
    }

    private static Path createPowerShell(Path systemRoot) throws Exception {
        Path powerShellDirectory = Files.createDirectories(systemRoot
                .resolve("System32")
                .resolve("WindowsPowerShell")
                .resolve("v1.0"));
        return Files.writeString(powerShellDirectory.resolve("powershell.exe"), "powershell executable");
    }

    private static ProcessResult result(int exitCode, String stdout, String stderr) {
        return new ProcessResult(exitCode, stdout, stderr, false, false, false, Duration.ZERO);
    }

    private static ProcessResult timeout() {
        return new ProcessResult(-1, "", "", false, false, true, Duration.ZERO);
    }

    private static ProcessStartException missingExecutable() {
        return new ProcessStartException(new java.io.IOException("not found"));
    }

    private static void assertCommands(List<ProcessSpec> specifications, List<List<String>> expectedCommands) {
        assertEquals(expectedCommands, specifications.stream().map(ProcessSpec::command).toList());
    }

    private static List<String> append(List<String> prefix, String... arguments) {
        List<String> command = new ArrayList<>(prefix);
        command.addAll(List.of(arguments));
        return List.copyOf(command);
    }

    private static final class FakeProcessExecutor implements ProcessExecutor {
        private final Deque<Object> outcomes;
        private final List<ProcessSpec> specifications = new ArrayList<>();

        private FakeProcessExecutor(Object... outcomes) {
            this.outcomes = new ArrayDeque<>(List.of(outcomes));
        }

        @Override
        public ProcessResult execute(ProcessSpec specification) {
            specifications.add(specification);
            Object outcome = outcomes.removeFirst();
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            return (ProcessResult) outcome;
        }

        private List<ProcessSpec> specifications() {
            return List.copyOf(specifications);
        }
    }
}
