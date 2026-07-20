package dev.codedefense.jetbrains.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.jetbrains.process.BridgeTransportException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepositoryInsightsServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void decodesTheExactSourceFreeShapeAndBuildsExactCommand() throws Exception {
        Path java = javaExecutable();
        Path cli = Files.createFile(temporaryDirectory.resolve("cli with spaces.jar"));
        Path root = Files.createDirectories(temporaryDirectory.resolve("real project"));
        RepositoryInsightsService service = service(cli, java, Map.of("PATH", "safe"));

        RepositoryInsightsView view = service.decode(validJson().getBytes(StandardCharsets.UTF_8));

        assertEquals(1, view.schemaVersion());
        assertEquals(3, view.attemptCount());
        assertEquals(2, view.defendedChangeCount());
        assertEquals(List.of(
                new CategoryInsightView("decision", 92),
                new CategoryInsightView("counterfactual", 54),
                new CategoryInsightView("test-prediction", 31)), view.categories());
        assertEquals("decision", view.strongestCategory());
        assertEquals("test-prediction", view.practiceCategory());
        assertEquals(List.of(33, 61, 84), view.recentOverallScores());
        assertEquals(List.of(java.toString(), "-jar", cli.toRealPath().toString(), "passport", "insights",
                root.toRealPath().toString(), "--format", "json", "--limit", "20"), service.command(root));
        assertEquals(Duration.ofSeconds(15), RepositoryInsightsService.DEFAULT_TIMEOUT);
        assertFalse(view.toString().contains("decision"));
    }

    @Test
    void viewsDefensivelyCopyCollections() {
        List<CategoryInsightView> categories = new ArrayList<>(List.of(
                new CategoryInsightView("decision", 92),
                new CategoryInsightView("counterfactual", 54),
                new CategoryInsightView("test-prediction", 31)));
        List<Integer> scores = new ArrayList<>(List.of(33, 61, 84));

        RepositoryInsightsView view = new RepositoryInsightsView(1, 3, 2, categories,
                "decision", "test-prediction", scores);
        categories.clear();
        scores.clear();

        assertEquals(3, view.categories().size());
        assertEquals(List.of(33, 61, 84), view.recentOverallScores());
        assertThrows(UnsupportedOperationException.class, () -> view.categories().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.recentOverallScores().add(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"schemaVersion\":1,\"schemaVersion\":1,\"attemptCount\":0,\"defendedChangeCount\":0,\"categories\":[],\"strongestCategory\":\"\",\"practiceCategory\":\"\",\"recentOverallScores\":[]}",
            "{\"schemaVersion\":1,\"attemptCount\":0,\"defendedChangeCount\":0,\"categories\":[],\"strongestCategory\":\"\",\"practiceCategory\":\"\",\"recentOverallScores\":[],\"unknown\":true}",
            "{\"schemaVersion\":1,\"attemptCount\":0,\"categories\":[],\"strongestCategory\":\"\",\"practiceCategory\":\"\",\"recentOverallScores\":[]}",
            "{\"schemaVersion\":1,\"attemptCount\":0,\"defendedChangeCount\":0,\"categories\":[],\"strongestCategory\":\"\",\"practiceCategory\":\"\",\"recentOverallScores\":[]} true"
    })
    void rejectsDuplicateUnknownMissingAndTrailingJson(String json) {
        BridgeTransportException failure = assertThrows(BridgeTransportException.class,
                () -> decoder().decode(json.getBytes(StandardCharsets.UTF_8)));

        assertSafeFailure(failure, json);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"schemaVersion\":2,\"attemptCount\":3,\"defendedChangeCount\":2,\"categories\":[{\"id\":\"decision\",\"averageScore\":92},{\"id\":\"counterfactual\",\"averageScore\":54},{\"id\":\"test-prediction\",\"averageScore\":31}],\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\",\"recentOverallScores\":[33,61,84]}",
            "{\"schemaVersion\":1,\"attemptCount\":21,\"defendedChangeCount\":2,\"categories\":[{\"id\":\"decision\",\"averageScore\":92},{\"id\":\"counterfactual\",\"averageScore\":54},{\"id\":\"test-prediction\",\"averageScore\":31}],\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\",\"recentOverallScores\":[33,61,84]}",
            "{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":4,\"categories\":[{\"id\":\"decision\",\"averageScore\":92},{\"id\":\"counterfactual\",\"averageScore\":54},{\"id\":\"test-prediction\",\"averageScore\":31}],\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\",\"recentOverallScores\":[33,61,84]}",
            "{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,\"categories\":[{\"id\":\"counterfactual\",\"averageScore\":54},{\"id\":\"decision\",\"averageScore\":92},{\"id\":\"test-prediction\",\"averageScore\":31}],\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\",\"recentOverallScores\":[33,61,84]}",
            "{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,\"categories\":[{\"id\":\"decision\",\"averageScore\":101},{\"id\":\"counterfactual\",\"averageScore\":54},{\"id\":\"test-prediction\",\"averageScore\":31}],\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\",\"recentOverallScores\":[33,61,84]}",
            "{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,\"categories\":[{\"id\":\"decision\",\"averageScore\":92},{\"id\":\"counterfactual\",\"averageScore\":54},{\"id\":\"test-prediction\",\"averageScore\":31}],\"strongestCategory\":\"unknown\",\"practiceCategory\":\"test-prediction\",\"recentOverallScores\":[33,61,84]}",
            "{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,\"categories\":[{\"id\":\"decision\",\"averageScore\":92},{\"id\":\"counterfactual\",\"averageScore\":54},{\"id\":\"test-prediction\",\"averageScore\":31}],\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\",\"recentOverallScores\":[101]}"
    })
    void rejectsScoreCountCategoryOrderAndSchemaViolations(String json) {
        assertThrows(BridgeTransportException.class,
                () -> decoder().decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsCanonicalEmptyHistory() {
        String json = "{\"schemaVersion\":1,\"attemptCount\":0,\"defendedChangeCount\":0,"
                + "\"categories\":[{\"id\":\"decision\",\"averageScore\":0},"
                + "{\"id\":\"counterfactual\",\"averageScore\":0},"
                + "{\"id\":\"test-prediction\",\"averageScore\":0}],"
                + "\"strongestCategory\":\"\",\"practiceCategory\":\"\","
                + "\"recentOverallScores\":[]}\n";

        RepositoryInsightsView view = decoder().decode(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(3, view.categories().size());
        assertTrue(view.recentOverallScores().isEmpty());
    }

    @Test
    void rejectsMalformedUtf8AndOversizedStdoutWithoutLeakingBytes() {
        BridgeTransportException malformed = assertThrows(BridgeTransportException.class,
                () -> decoder().decode(new byte[] {(byte) 0xc3, (byte) 0x28}));
        assertSafeFailure(malformed, "c3");

        byte[] oversized = new byte[256 * 1024 + 1];
        assertThrows(BridgeTransportException.class, () -> decoder().decode(oversized));
    }

    @Test
    void drainsAndRejectsOversizedStdoutFromARealChild() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("overflow-child"));
        RepositoryInsightsService service = service(childJar(), javaExecutable(), Map.of("PATH", "safe"));

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertThrows(BridgeTransportException.class, () -> service.refresh(root)));
    }

    @Test
    void realChildUsesExactWorkingDirectoryAndMinimalEnvironmentWhileDrainingLargeStderr() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("valid-child"));
        RepositoryInsightsService service = service(childJar(), javaExecutable(), Map.of(
                "PATH", "safe-path", "SystemRoot", "safe-root", "WINDIR", "safe-windir",
                "PATHEXT", ".EXE", "PRIVATE_SECRET", "do-not-inherit"));

        RepositoryInsightsView view = service.refresh(root);

        assertEquals(3, view.attemptCount());
        List<String> observation = Files.readAllLines(root.resolve("insights-observation.txt"));
        assertEquals(root.toRealPath().toString(), observation.get(0));
        assertEquals("passport|insights|" + root.toRealPath() + "|--format|json|--limit|20", observation.get(1));
        assertEquals("false", observation.get(2));
        assertTrue(observation.get(3).contains("PATH"));
        assertFalse(observation.get(3).contains("PRIVATE_SECRET"));
    }

    @Test
    void rejectsNonzeroExitWithoutLeakingChildOutputOrRoot() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("private-root-fail"));
        RepositoryInsightsService service = service(childJar(), javaExecutable(), Map.of("PATH", "safe"));

        BridgeTransportException failure = assertThrows(BridgeTransportException.class,
                () -> service.refresh(root));

        assertSafeFailure(failure, "private-child-output", "private-child-error", "private-root-fail");
    }

    @Test
    void timeoutTerminatesTheWholeProcessTreeWithinDeadlineAndGrace() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("tree-timeout"));
        RepositoryInsightsService service = new RepositoryInsightsService(childJar(), javaExecutable(),
                Map.of("PATH", "safe"), Duration.ofMillis(250), Duration.ofMillis(100));
        long started = System.nanoTime();

        assertTimeoutPreemptively(Duration.ofSeconds(3),
                () -> assertThrows(BridgeTransportException.class, () -> service.refresh(root)));

        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1_500);
        Path pidFile = root.resolve("descendant.pid");
        assertTrue(Files.exists(pidFile));
        long pid = Long.parseLong(Files.readString(pidFile));
        assertTrue(ProcessHandle.of(pid).isEmpty() || !ProcessHandle.of(pid).orElseThrow().isAlive());
    }

    @Test
    void interruptStatusIsPreservedAndChildIsTerminated() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("sleep-interrupt"));
        RepositoryInsightsService service = new RepositoryInsightsService(childJar(), javaExecutable(),
                Map.of("PATH", "safe"), Duration.ofSeconds(5), Duration.ofMillis(50));

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
        Path cli = Files.createFile(temporaryDirectory.resolve("empty.jar"));
        Path java = javaExecutable();

        assertThrows(IllegalArgumentException.class, () -> new RepositoryInsightsService(
                cli, java, Map.of(), Duration.ZERO, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryInsightsService(
                cli, java, Map.of(), Duration.ofSeconds(1), Duration.ZERO));
    }

    private RepositoryInsightsService decoder() {
        return new RepositoryInsightsService(temporaryDirectory.resolve("cli.jar"), javaExecutable(), Map.of(),
                Duration.ofSeconds(1), Duration.ofMillis(10), (command, directory, environment) -> {
                    throw new AssertionError("decoder tests must not launch a process");
                });
    }

    private RepositoryInsightsService service(Path jar, Path java, Map<String, String> environment) {
        return new RepositoryInsightsService(jar, java, environment, Duration.ofSeconds(5), Duration.ofMillis(200));
    }

    private Path javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
                .toAbsolutePath().normalize();
    }

    private Path childJar() throws Exception {
        Path jar = temporaryDirectory.resolve("insights-child.jar");
        if (Files.exists(jar)) return jar;
        var manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, InsightsChild.class.getName());
        String resource = InsightsChild.class.getName().replace('.', '/') + ".class";
        try (var output = new java.util.jar.JarOutputStream(Files.newOutputStream(jar), manifest);
                var input = InsightsChild.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("child class is unavailable");
            output.putNextEntry(new java.util.jar.JarEntry(resource));
            input.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }

    private String validJson() {
        return "{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,"
                + "\"categories\":[{\"id\":\"decision\",\"averageScore\":92},"
                + "{\"id\":\"counterfactual\",\"averageScore\":54},"
                + "{\"id\":\"test-prediction\",\"averageScore\":31}],"
                + "\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\","
                + "\"recentOverallScores\":[33,61,84]}\n";
    }

    private void assertSafeFailure(Throwable failure, String... forbidden) {
        assertEquals("CodeDefense repository insights are unavailable.", failure.getMessage());
        for (String marker : forbidden) assertFalse(failure.toString().contains(marker));
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public static final class InsightsChild {
        private InsightsChild() { }

        public static void main(String[] args) throws Exception {
            if (args.length == 1 && args[0].equals("sleep-child")) {
                Thread.sleep(60_000);
                return;
            }
            Path root = Path.of(args[2]);
            String mode = root.getFileName().toString();
            if (mode.contains("fail")) {
                System.out.print("private-child-output");
                System.err.print("private-child-error");
                System.exit(23);
            }
            if (mode.contains("overflow")) {
                System.out.print("x".repeat(512 * 1024));
                System.out.flush();
                return;
            }
            if (mode.contains("tree-timeout")) {
                Process descendant = new ProcessBuilder(javaCommand(), "-cp", ownJar(),
                        InsightsChild.class.getName(), "sleep-child").start();
                Files.writeString(root.resolve("descendant.pid"), Long.toString(descendant.pid()));
                Thread.sleep(60_000);
                return;
            }
            if (mode.contains("sleep")) {
                Thread.sleep(60_000);
                return;
            }
            Files.write(root.resolve("insights-observation.txt"), List.of(
                    Path.of("").toAbsolutePath().normalize().toString(), String.join("|", args),
                    Boolean.toString(System.getenv().containsKey("PRIVATE_SECRET")),
                    String.join(",", new java.util.TreeSet<>(System.getenv().keySet()))));
            System.err.print("x".repeat(512 * 1024));
            System.err.flush();
            System.out.print("{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,"
                    + "\"categories\":[{\"id\":\"decision\",\"averageScore\":92},"
                    + "{\"id\":\"counterfactual\",\"averageScore\":54},"
                    + "{\"id\":\"test-prediction\",\"averageScore\":31}],"
                    + "\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\","
                    + "\"recentOverallScores\":[33,61,84]}\n");
        }

        private static String javaCommand() {
            return Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java")
                    .toString();
        }

        private static String ownJar() throws Exception {
            return Path.of(InsightsChild.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toString();
        }
    }
}
