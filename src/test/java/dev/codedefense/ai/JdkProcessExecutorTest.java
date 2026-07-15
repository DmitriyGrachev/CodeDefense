package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkProcessExecutorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sendsUtf8StandardInputAndCapturesStdout() {
        ProcessResult result = new JdkProcessExecutor().execute(spec("echo", "Hello, Привет 👋"));

        assertEquals(0, result.exitCode());
        assertEquals("Hello, Привет 👋", result.stdout());
        assertEquals("", result.stderr());
        assertFalse(result.timedOut());
    }

    @Test
    void capturesStdoutAndStderr() {
        ProcessResult result = new JdkProcessExecutor().execute(
                spec("both", "", "stdout value", "stderr value"));

        assertEquals(0, result.exitCode());
        assertEquals("stdout value", result.stdout());
        assertEquals("stderr value", result.stderr());
        assertFalse(result.timedOut());
    }

    @Test
    void retainsNonzeroExitCodeAndArgumentsContainingSpaces() {
        ProcessResult result = new JdkProcessExecutor().execute(
                spec("fail", "", "17", "diagnostic with spaces"));

        assertEquals(17, result.exitCode());
        assertEquals("diagnostic with spaces", result.stderr());
        assertFalse(result.timedOut());
    }

    @Test
    void preservesIntentionalExitCodeWithoutTimeout() {
        ProcessResult result = new JdkProcessExecutor().execute(spec("fail", "", "143", "intentional"));

        assertEquals(143, result.exitCode());
        assertFalse(result.timedOut());
    }

    @Test
    void marksStdoutAsTruncatedWhenItsCaptureLimitIsExceeded() {
        ProcessResult result = new JdkProcessExecutor().execute(
                spec("echo", "12345", 3, 1024, Duration.ofSeconds(2), Map.of()));

        assertEquals("123", result.stdout());
        assertTrue(result.stdoutTruncated());
        assertFalse(result.stderrTruncated());
        assertFalse(result.timedOut());
    }

    @Test
    void marksStderrAsTruncatedWhenItsCaptureLimitIsExceeded() {
        ProcessResult result = new JdkProcessExecutor().execute(
                spec("stderr", "", 1024, 3, Duration.ofSeconds(2), Map.of(), "5"));

        assertEquals("eee", result.stderr());
        assertFalse(result.stdoutTruncated());
        assertTrue(result.stderrTruncated());
        assertFalse(result.timedOut());
    }

    @Test
    void drainsLargeStderrAfterItsCaptureLimitIsExceeded() {
        ProcessResult result = new JdkProcessExecutor().execute(
                spec("stderr", "", 1024, 128, Duration.ofSeconds(3), Map.of(), "1048576"));

        assertEquals(0, result.exitCode());
        assertEquals(128, result.stderr().length());
        assertTrue(result.stderrTruncated());
        assertFalse(result.timedOut());
    }

    @Test
    void marksKilledProcessAsTimedOut() {
        ProcessResult result = new JdkProcessExecutor().execute(
                spec("sleep", "", 1024, 1024, Duration.ofMillis(250), Map.of(), "5000"));

        assertTrue(result.timedOut());
        assertTrue(result.duration().compareTo(Duration.ofMillis(200)) >= 0);
        assertTrue(result.duration().compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void blockedStdinWriterDoesNotMaskTimeout() {
        ProcessResult result = new JdkProcessExecutor().execute(spec(
                "sleep-without-reading-stdin",
                "x".repeat(1024 * 1024),
                1024,
                1024,
                Duration.ofMillis(250),
                Map.of(),
                "5000"));

        assertTrue(result.timedOut());
        assertTrue(result.duration().compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void descendantCannotKeepExecutionOpenPastDeadline() {
        long started = System.nanoTime();
        ProcessResult result = new JdkProcessExecutor().execute(spec(
                "spawn-descendant", "", 1024, 1024, Duration.ofMillis(250), Map.of(), "5000"));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertTrue(result.timedOut());
        assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0);
    }

    @Test
    void appliesOnlyTheEnvironmentFromProcessSpec() {
        Map<String, String> environment = requiredPlatformEnvironment();
        environment.put("CODEDEFENSE_VISIBLE", "visible-value");
        ProcessResult explicit = new JdkProcessExecutor().execute(spec(
                "both", "", 1024, 1024, Duration.ofSeconds(2), environment,
                "explicit=", "fixture-stderr", "CODEDEFENSE_VISIBLE"));

        String inheritedVariable = System.getenv().keySet().stream()
                .filter(key -> !requiredPlatformEnvironment().containsKey(key))
                .findFirst()
                .orElseThrow();
        ProcessResult absent = new JdkProcessExecutor().execute(spec(
                "both", "", 1024, 1024, Duration.ofSeconds(2), requiredPlatformEnvironment(),
                "absent=", "fixture-stderr", inheritedVariable));

        assertEquals("explicit=visible-value", explicit.stdout());
        assertEquals("absent=<absent>", absent.stdout());
    }

    @Test
    void preservesTheInterruptFlagWhenExecutionIsInterrupted() throws InterruptedException {
        AtomicBoolean interruptFlagPreserved = new AtomicBoolean();
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                new JdkProcessExecutor().execute(
                        spec("sleep", "", 1024, 1024, Duration.ofSeconds(5), Map.of(), "5000"));
            } catch (IllegalStateException exception) {
                interruptFlagPreserved.set(Thread.currentThread().isInterrupted());
            }
        });

        Thread.sleep(100);
        worker.interrupt();
        worker.join(Duration.ofSeconds(2));

        assertFalse(worker.isAlive());
        assertTrue(interruptFlagPreserved.get());
    }

    private ProcessSpec spec(String mode, String standardInput, String... arguments) {
        return spec(mode, standardInput, 1024, 1024, Duration.ofSeconds(2), Map.of(), arguments);
    }

    private ProcessSpec spec(
            String mode,
            String standardInput,
            int maximumStdoutBytes,
            int maximumStderrBytes,
            Duration timeout,
            Map<String, String> environment,
            String... arguments) {
        return new ProcessSpec(
                fixtureCommand(mode, arguments),
                temporaryDirectory,
                environment,
                standardInput,
                timeout,
                Duration.ofMillis(100),
                maximumStdoutBytes,
                maximumStderrBytes);
    }

    private List<String> fixtureCommand(String mode, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ProcessFixtureMain.class.getName());
        command.add(mode);
        command.addAll(List.of(arguments));
        return command;
    }

    private static Path javaExecutable() {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }

    private static Map<String, String> requiredPlatformEnvironment() {
        Map<String, String> environment = new HashMap<>();
        copyIfPresent(environment, "SystemRoot");
        copyIfPresent(environment, "WINDIR");
        return environment;
    }

    private static void copyIfPresent(Map<String, String> target, String key) {
        String value = System.getenv(key);
        if (value != null) {
            target.put(key, value);
        }
    }
}
