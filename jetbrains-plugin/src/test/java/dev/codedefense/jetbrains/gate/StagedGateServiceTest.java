package dev.codedefense.jetbrains.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import dev.codedefense.jetbrains.process.BridgeTransportException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StagedGateServiceTest {
    private static final String HASH = "0123456789abcdef".repeat(4);
    @TempDir Path temporaryDirectory;

    @Test
    void decodesValidatedViewAndBuildsTheExactSourceFreeCommand() throws Exception {
        Path java = javaExecutable();
        Path cli = Files.createFile(temporaryDirectory.resolve("cli with spaces.jar"));
        Path root = Files.createDirectories(temporaryDirectory.resolve("real project"));
        var service = service(cli, java, Map.of("PATH", "safe-path", "PRIVATE_SECRET", "do-not-inherit"));

        StagedGateView view = service.decode(validJson("CURRENT", "IDENTITY_MATCH", HASH, 2, "[]")
                .getBytes(StandardCharsets.UTF_8));

        assertEquals(StagedGateView.State.CURRENT, view.state());
        assertEquals(StagedGateView.Reason.IDENTITY_MATCH, view.reason());
        assertEquals("0123456789ab", view.shortFingerprint());
        assertEquals(List.of(java.toString(), "-jar", cli.toRealPath().toString(), "passport", "gate",
                "--staged", root.toRealPath().toString(), "--format", "json"), service.command(root));
        assertEquals(Duration.ofSeconds(15), StagedGateService.DEFAULT_TIMEOUT);
        assertFalse(view.toString().contains(HASH));
        assertFalse(view.toString().contains("relativePaths="));
    }

    @Test
    void defensivelyCopiesPathsAndKeepsOrdinaryDisplaySourceFree() {
        var paths = new java.util.ArrayList<>(List.of("src/B.java", "src/A.java"));
        var view = new StagedGateView(1, StagedGateView.State.EXPIRED,
                StagedGateView.Reason.IDENTITY_CHANGED, HASH, 0, 2, 3, 4, paths);

        paths.clear();

        assertEquals(List.of("src/A.java", "src/B.java"), view.relativePaths());
        assertThrows(UnsupportedOperationException.class, () -> view.relativePaths().add("src/C.java"));
        assertFalse(view.toString().contains("src/A.java"));
        assertFalse(view.toString().contains(HASH));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"protocolVersion\":1,\"protocolVersion\":1,\"state\":\"NO_STAGED_CHANGE\",\"reason\":\"NO_INDEX_ENTRIES\",\"diffFingerprint\":\"\",\"attemptNumber\":0,\"stagedFileCount\":0,\"addedLines\":0,\"deletedLines\":0,\"relativePaths\":[]}",
            "{\"protocolVersion\":1,\"state\":\"NO_STAGED_CHANGE\",\"reason\":\"NO_INDEX_ENTRIES\",\"diffFingerprint\":\"\",\"attemptNumber\":0,\"stagedFileCount\":0,\"addedLines\":0,\"deletedLines\":0,\"relativePaths\":[],\"unknown\":true}",
            "{\"protocolVersion\":1,\"state\":\"NO_STAGED_CHANGE\",\"reason\":\"NO_INDEX_ENTRIES\",\"diffFingerprint\":\"\",\"attemptNumber\":0,\"stagedFileCount\":0,\"addedLines\":0,\"relativePaths\":[]}",
            "{\"protocolVersion\":1,\"state\":\"NO_STAGED_CHANGE\",\"reason\":\"NO_INDEX_ENTRIES\",\"diffFingerprint\":\"\",\"attemptNumber\":0,\"stagedFileCount\":0,\"addedLines\":0,\"deletedLines\":0,\"relativePaths\":[]} true"
    })
    void rejectsDuplicateUnknownMissingAndTrailingJson(String json) {
        BridgeTransportException failure = assertThrows(BridgeTransportException.class,
                () -> decoder().decode(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("CodeDefense staged Passport status is unavailable.", failure.getMessage());
        assertFalse(failure.toString().contains(json));
    }

    @Test
    void rejectsMalformedUtf8WithoutLeakingRawBytes() {
        byte[] invalid = {(byte) 0xc3, (byte) 0x28};

        BridgeTransportException failure = assertThrows(BridgeTransportException.class,
                () -> decoder().decode(invalid));

        assertEquals("CodeDefense staged Passport status is unavailable.", failure.getMessage());
        assertFalse(failure.toString().contains("c3"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BROKEN|NO_INDEX_ENTRIES||0|0",
            "NO_STAGED_CHANGE|NONE||0|0",
            "UNDEFENDED|IDENTITY_MATCH|hash|0|1",
            "CURRENT|IDENTITY_MATCH|hash|0|1",
            "CURRENT|IDENTITY_MATCH|hash|2|0",
            "EXPIRED|IDENTITY_CHANGED|hash|3|1",
            "UNAVAILABLE|GIT_CAPTURE_FAILED|hash|0|1"
    })
    void rejectsInvalidEnumsReasonsStateMetadataAndHash(String values) {
        String[] value = values.split("\\|", -1);
        String hash = value[2].equals("hash") ? "not-a-sha256" : value[2];
        String json = validJson(value[0], value[1], hash, Integer.parseInt(value[3]), "[]")
                .replace("\"stagedFileCount\":1", "\"stagedFileCount\":" + value[4]);

        assertThrows(BridgeTransportException.class, () -> decoder().decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.txt", "src/../../secret.txt", "/etc/passwd", "C:secret.txt",
            "C:\\secret.txt", "\\\\server\\share\\secret.txt"})
    void rejectsTraversalAbsoluteAndDriveRelativePaths(String path) {
        String escaped = path.replace("\\", "\\\\");
        String json = validJson("EXPIRED", "IDENTITY_CHANGED", HASH, 0, "[\"" + escaped + "\"]");

        BridgeTransportException failure = assertThrows(BridgeTransportException.class,
                () -> decoder().decode(json.getBytes(StandardCharsets.UTF_8)));

        assertFalse(failure.toString().contains(path));
    }

    @Test
    void rejectsOversizedOutputBeforeJsonParsing() {
        byte[] oversized = new byte[256 * 1024 + 1];

        assertThrows(BridgeTransportException.class, () -> decoder().decode(oversized));
    }

    @Test
    void realChildUsesExactWorkingDirectoryAndMinimalEnvironmentWhileDrainingLargeStderr() throws Exception {
        Path jar = childJar();
        Path root = Files.createDirectories(temporaryDirectory.resolve("valid-child"));
        var service = service(jar, javaExecutable(), Map.of(
                "PATH", "safe-path", "SystemRoot", "safe-root", "WINDIR", "safe-windir",
                "PATHEXT", ".EXE", "PRIVATE_SECRET", "do-not-inherit"));

        StagedGateView view = service.refresh(root);

        assertEquals(StagedGateView.State.CURRENT, view.state());
        List<String> observation = Files.readAllLines(root.resolve("gate-observation.txt"));
        assertEquals(root.toRealPath().toString(), observation.get(0));
        assertEquals("passport|gate|--staged|" + root.toRealPath() + "|--format|json", observation.get(1));
        assertEquals("false", observation.get(2));
        assertTrue(observation.get(3).contains("PATH"));
        assertFalse(observation.get(3).contains("PRIVATE_SECRET"));
    }

    @Test
    void rejectsNonzeroExitAndDiscardsChildOutputAndRawRootFromDiagnostics() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("private-root-fail"));
        var service = service(childJar(), javaExecutable(), Map.of("PATH", "safe"));

        BridgeTransportException failure = assertThrows(BridgeTransportException.class,
                () -> service.refresh(root));

        assertEquals("CodeDefense staged Passport status is unavailable.", failure.getMessage());
        assertFalse(failure.toString().contains("private-child-output"));
        assertFalse(failure.toString().contains("private-root-fail"));
    }

    @Test
    void timeoutTerminatesTheWholeProcessTreeAfterPositiveGrace() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("tree-timeout"));
        var service = new StagedGateService(childJar(), javaExecutable(), Map.of("PATH", "safe"),
                Duration.ofMillis(250), Duration.ofMillis(100));

        assertThrows(BridgeTransportException.class, () -> service.refresh(root));

        Path pidFile = root.resolve("descendant.pid");
        assertTrue(Files.exists(pidFile));
        long pid = Long.parseLong(Files.readString(pidFile));
        assertTrue(ProcessHandle.of(pid).isEmpty() || !ProcessHandle.of(pid).orElseThrow().isAlive());
    }

    @Test
    void exitedParentWithDescendantHoldingPipesFailsWithinOneDeadlineAndGrace() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("orphan-pipes"));
        Duration timeout = Duration.ofMillis(500);
        Duration grace = Duration.ofMillis(250);
        var service = new StagedGateService(childJar(), javaExecutable(), Map.of("PATH", "safe"),
                timeout, grace);
        long started = System.nanoTime();

        try {
            BridgeTransportException failure = assertTimeoutPreemptively(Duration.ofSeconds(3),
                    () -> assertThrows(BridgeTransportException.class, () -> service.refresh(root)));
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

            assertEquals("CodeDefense staged Passport status is unavailable.", failure.getMessage());
            assertTrue(elapsedMillis < 1_500, "refresh exceeded deadline plus bounded scheduling tolerance");
            long pid = Long.parseLong(Files.readString(root.resolve("descendant.pid")));
            assertTrue(ProcessHandle.of(pid).isEmpty() || !ProcessHandle.of(pid).orElseThrow().isAlive());
        } finally {
            Path pidFile = root.resolve("descendant.pid");
            if (Files.exists(pidFile)) {
                ProcessHandle.of(Long.parseLong(Files.readString(pidFile))).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    @Test
    void interruptStatusIsPreservedAndTheChildIsTerminated() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("sleep-interrupt"));
        var service = new StagedGateService(childJar(), javaExecutable(), Map.of("PATH", "safe"),
                Duration.ofSeconds(5), Duration.ofMillis(50));

        Thread.currentThread().interrupt();
        try {
            assertThrows(BridgeTransportException.class, () -> service.refresh(root));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void constructorRejectsNonpositiveTimeoutOrTerminationGrace() throws Exception {
        Path jar = Files.createFile(temporaryDirectory.resolve("empty.jar"));
        Path java = javaExecutable();

        assertThrows(IllegalArgumentException.class, () -> new StagedGateService(jar, java, Map.of(),
                Duration.ZERO, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new StagedGateService(jar, java, Map.of(),
                Duration.ofSeconds(1), Duration.ZERO));
    }

    private StagedGateService decoder() {
        return new StagedGateService(temporaryDirectory.resolve("cli.jar"), javaExecutable(), Map.of(),
                Duration.ofSeconds(1), Duration.ofMillis(10), (command, directory, environment) -> {
                    throw new AssertionError("decoder tests must not launch a process");
                });
    }

    private StagedGateService service(Path jar, Path java, Map<String, String> environment) {
        return new StagedGateService(jar, java, environment, Duration.ofSeconds(5), Duration.ofMillis(200));
    }

    private Path javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
                .toAbsolutePath().normalize();
    }

    private Path childJar() throws Exception {
        Path jar = temporaryDirectory.resolve("gate-child.jar");
        if (Files.exists(jar)) return jar;
        var manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, GateChild.class.getName());
        String resource = GateChild.class.getName().replace('.', '/') + ".class";
        try (var output = new java.util.jar.JarOutputStream(Files.newOutputStream(jar), manifest);
                var input = GateChild.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("child class is unavailable");
            output.putNextEntry(new java.util.jar.JarEntry(resource));
            input.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }

    private String validJson(String state, String reason, String hash, int attempt, String paths) {
        int files = state.equals("UNDEFENDED") || state.equals("CURRENT") || state.equals("EXPIRED") ? 1 : 0;
        int lines = files == 0 ? 0 : 2;
        return ("{\"protocolVersion\":1,\"state\":\"%s\",\"reason\":\"%s\","
                + "\"diffFingerprint\":\"%s\",\"attemptNumber\":%d,\"stagedFileCount\":%d,"
                + "\"addedLines\":%d,\"deletedLines\":0,\"relativePaths\":%s}\n")
                .formatted(state, reason, hash, attempt, files, lines, paths);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public static final class GateChild {
        private GateChild() { }

        public static void main(String[] args) throws Exception {
            if (args.length == 1 && args[0].equals("sleep-child")) {
                Thread.sleep(60_000);
                return;
            }
            Path root = Path.of(args[3]);
            String mode = root.getFileName().toString();
            if (mode.contains("fail")) {
                System.out.print("private-child-output");
                System.err.print("private-child-error");
                System.exit(23);
            }
            if (mode.contains("tree-timeout")) {
                String java = Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java")
                        .toString();
                String jar = Path.of(GateChild.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .toString();
                Process descendant = new ProcessBuilder(java, "-cp", jar, GateChild.class.getName(), "sleep-child")
                        .start();
                Files.writeString(root.resolve("descendant.pid"), Long.toString(descendant.pid()));
                Thread.sleep(60_000);
                return;
            }
            if (mode.contains("orphan-pipes")) {
                String java = Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java")
                        .toString();
                String jar = Path.of(GateChild.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .toString();
                Process descendant = new ProcessBuilder(java, "-cp", jar, GateChild.class.getName(), "sleep-child")
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start();
                Files.writeString(root.resolve("descendant.pid"), Long.toString(descendant.pid()));
                Thread.sleep(200);
                return;
            }
            if (mode.contains("sleep")) {
                Thread.sleep(60_000);
                return;
            }
            Files.write(root.resolve("gate-observation.txt"), List.of(
                    Path.of("").toAbsolutePath().normalize().toString(), String.join("|", args),
                    Boolean.toString(System.getenv().containsKey("PRIVATE_SECRET")),
                    String.join(",", new java.util.TreeSet<>(System.getenv().keySet()))));
            System.err.print("x".repeat(512 * 1024));
            System.err.flush();
            System.out.print(("{\"protocolVersion\":1,\"state\":\"CURRENT\","
                    + "\"reason\":\"IDENTITY_MATCH\",\"diffFingerprint\":\"%s\","
                    + "\"attemptNumber\":2,\"stagedFileCount\":1,\"addedLines\":2,"
                    + "\"deletedLines\":0,\"relativePaths\":[]}\n")
                    .formatted("0123456789abcdef".repeat(4)));
        }
    }
}
