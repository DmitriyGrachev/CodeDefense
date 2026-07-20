package dev.codedefense.jetbrains.insights;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.codedefense.jetbrains.process.BridgeTransportException;
import dev.codedefense.jetbrains.process.JavaExecutableResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes and strictly decodes the source-free repository insights command. */
public final class RepositoryInsightsService {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    static final int MAXIMUM_OUTPUT_BYTES = 256 * 1024;
    private static final Duration DEFAULT_TERMINATION_GRACE = Duration.ofSeconds(2);
    private static final Set<String> FIELDS = Set.of("schemaVersion", "attemptCount", "defendedChangeCount",
            "categories", "strongestCategory", "practiceCategory", "recentOverallScores");
    private static final Set<String> CATEGORY_FIELDS = Set.of("id", "averageScore");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final Path cliJar;
    private final Path javaExecutable;
    private final Map<String, String> environment;
    private final Duration timeout;
    private final Duration terminationGrace;
    private final ProcessStarter processStarter;

    public static RepositoryInsightsService production(Path cliJar) {
        Path java = new JavaExecutableResolver().resolve(Path.of(System.getProperty("java.home")));
        return new RepositoryInsightsService(cliJar, java, System.getenv(), DEFAULT_TIMEOUT,
                DEFAULT_TERMINATION_GRACE);
    }

    RepositoryInsightsService(Path cliJar, Path javaExecutable, Map<String, String> sourceEnvironment,
            Duration timeout, Duration terminationGrace) {
        this(cliJar, javaExecutable, sourceEnvironment, timeout, terminationGrace,
                RepositoryInsightsService::start);
    }

    RepositoryInsightsService(Path cliJar, Path javaExecutable, Map<String, String> sourceEnvironment,
            Duration timeout, Duration terminationGrace, ProcessStarter processStarter) {
        this.cliJar = Objects.requireNonNull(cliJar, "cliJar").toAbsolutePath().normalize();
        this.javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable").toAbsolutePath().normalize();
        this.environment = allowedEnvironment(sourceEnvironment);
        this.timeout = positive(timeout, "timeout");
        this.terminationGrace = positive(terminationGrace, "terminationGrace");
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
    }

    public RepositoryInsightsView refresh(Path projectRoot) {
        long operationDeadline = deadline(System.nanoTime(), timeout);
        Path root = realProjectRoot(projectRoot);
        Process process;
        try {
            process = processStarter.start(command(root), root, environment);
        } catch (IOException | RuntimeException exception) {
            throw failure();
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        AtomicBoolean overflow = new AtomicBoolean();
        AtomicBoolean drainFailure = new AtomicBoolean();
        ProcessTree processTree = new ProcessTree(process.toHandle());
        Thread stdoutDrain = Thread.ofVirtual().name("codedefense-insights-stdout").start(
                () -> drain(process.getInputStream(), stdout, overflow, drainFailure));
        Thread stderrDrain = Thread.ofVirtual().name("codedefense-insights-stderr").start(
                () -> drain(process.getErrorStream(), null, null, drainFailure));
        try {
            if (!awaitRoot(process, processTree, operationDeadline)) {
                terminateTree(process, processTree, stdoutDrain, stderrDrain, cleanupDeadline(operationDeadline));
                throw failure();
            }
            if (process.exitValue() != 0) {
                terminateTree(process, processTree, stdoutDrain, stderrDrain, cleanupDeadline(operationDeadline));
                throw failure();
            }
            if (!awaitDrains(processTree, operationDeadline, stdoutDrain, stderrDrain)) {
                terminateTree(process, processTree, stdoutDrain, stderrDrain, cleanupDeadline(operationDeadline));
                throw failure();
            }
            processTree.capture();
            if (overflow.get() || drainFailure.get() || processTree.anyAlive()) {
                terminateTree(process, processTree, stdoutDrain, stderrDrain, cleanupDeadline(operationDeadline));
                throw failure();
            }
            return decode(stdout.toByteArray());
        } catch (InterruptedException exception) {
            terminateTree(process, processTree, stdoutDrain, stderrDrain, cleanupDeadline(operationDeadline));
            Thread.currentThread().interrupt();
            throw failure();
        }
    }

    List<String> command(Path projectRoot) {
        Path root = realProjectRoot(projectRoot);
        Path java = realRegularFile(javaExecutable);
        Path jar = realRegularFile(cliJar);
        return List.of(java.toString(), "-jar", jar.toString(), "passport", "insights", root.toString(),
                "--format", "json", "--limit", "20");
    }

    RepositoryInsightsView decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAXIMUM_OUTPUT_BYTES) throw failure();
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw failure();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) throw failure();
            exactFields(root, FIELDS);

            JsonNode categoryNodes = root.get("categories");
            if (categoryNodes == null || !categoryNodes.isArray() || categoryNodes.size() != 3) throw failure();
            List<CategoryInsightView> categories = new ArrayList<>(3);
            for (JsonNode category : categoryNodes) {
                if (!category.isObject()) throw failure();
                exactFields(category, CATEGORY_FIELDS);
                categories.add(new CategoryInsightView(text(category, "id"), integer(category, "averageScore")));
            }

            JsonNode scoreNodes = root.get("recentOverallScores");
            if (scoreNodes == null || !scoreNodes.isArray() || scoreNodes.size() > 10) throw failure();
            List<Integer> scores = new ArrayList<>(scoreNodes.size());
            for (JsonNode score : scoreNodes) scores.add(integer(score));

            return new RepositoryInsightsView(integer(root, "schemaVersion"), integer(root, "attemptCount"),
                    integer(root, "defendedChangeCount"), categories, text(root, "strongestCategory"),
                    text(root, "practiceCategory"), scores);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw failure();
        }
    }

    private boolean awaitRoot(Process process, ProcessTree tree, long deadline) throws InterruptedException {
        while (process.isAlive()) {
            tree.capture();
            long remaining = remaining(deadline);
            if (remaining <= 0) return false;
            process.waitFor(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)), TimeUnit.NANOSECONDS);
        }
        tree.capture();
        return true;
    }

    private static boolean awaitDrains(ProcessTree tree, long deadline, Thread... drains)
            throws InterruptedException {
        while (anyAlive(drains)) {
            tree.capture();
            long remaining = remaining(deadline);
            if (remaining <= 0) return false;
            boundedJoin(firstAlive(drains), Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)));
        }
        return true;
    }

    private static void terminateTree(Process process, ProcessTree tree, Thread stdoutDrain, Thread stderrDrain,
            long cleanupDeadline) {
        boolean interrupted = false;
        long gracefulDeadline = deadline(System.nanoTime(),
                Duration.ofNanos(Math.max(1, remaining(cleanupDeadline) / 2)));
        try {
            terminatePhase(tree, gracefulDeadline, false);
        } catch (InterruptedException exception) {
            interrupted = true;
        }
        try {
            terminatePhase(tree, cleanupDeadline, true);
        } catch (InterruptedException exception) {
            interrupted = true;
        }
        close(process.getInputStream());
        close(process.getErrorStream());
        close(process.getOutputStream());
        try {
            awaitDrains(tree, cleanupDeadline, stdoutDrain, stderrDrain);
        } catch (InterruptedException exception) {
            interrupted = true;
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static void terminatePhase(ProcessTree tree, long deadline, boolean forcibly)
            throws InterruptedException {
        do {
            tree.capture();
            for (ProcessHandle handle : tree.handles()) {
                if (!handle.isAlive()) continue;
                try {
                    if (forcibly) handle.destroyForcibly();
                    else handle.destroy();
                } catch (RuntimeException ignored) {
                    // Continue bounded cleanup without exposing process details.
                }
            }
            if (!tree.anyAlive()) return;
            long remaining = remaining(deadline);
            if (remaining <= 0) return;
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)));
        } while (remaining(deadline) > 0);
    }

    private long cleanupDeadline(long operationDeadline) {
        long absoluteLimit = deadline(operationDeadline, terminationGrace);
        long graceFromNow = deadline(System.nanoTime(), terminationGrace);
        return earlier(absoluteLimit, graceFromNow);
    }

    private static void drain(InputStream input, ByteArrayOutputStream capture, AtomicBoolean overflow,
            AtomicBoolean failure) {
        try (input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (capture != null && read > 0) {
                    int accepted = Math.min(read, MAXIMUM_OUTPUT_BYTES - capture.size());
                    if (accepted > 0) capture.write(buffer, 0, accepted);
                    if (accepted < read) overflow.set(true);
                }
            }
        } catch (IOException exception) {
            failure.set(true);
        }
    }

    private static Process start(List<String> command, Path directory, Map<String, String> environment)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().clear();
        builder.environment().putAll(environment);
        return builder.start();
    }

    private static Map<String, String> allowedEnvironment(Map<String, String> source) {
        Objects.requireNonNull(source, "sourceEnvironment");
        Map<String, String> allowed = new LinkedHashMap<>();
        copy(source, allowed, "PATH");
        copy(source, allowed, "SystemRoot");
        copy(source, allowed, "WINDIR");
        copy(source, allowed, "PATHEXT");
        return Map.copyOf(allowed);
    }

    private static void copy(Map<String, String> source, Map<String, String> target, String canonicalName) {
        source.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null
                        && entry.getKey().equalsIgnoreCase(canonicalName))
                .findFirst().ifPresent(entry -> target.put(canonicalName, entry.getValue()));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().length() > 4096) throw failure();
        return value.textValue();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw failure();
        return value.intValue();
    }

    private static int integer(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) throw failure();
        return node.intValue();
    }

    private static void exactFields(JsonNode node, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw failure();
    }

    private static Path realProjectRoot(Path root) {
        if (root == null) throw failure();
        try {
            Path normalized = root.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
                throw failure();
            }
            return normalized.toRealPath();
        } catch (IOException | RuntimeException exception) {
            throw failure();
        }
    }

    private static Path realRegularFile(Path file) {
        try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw failure();
            return file.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | RuntimeException exception) {
            throw failure();
        }
    }

    private static Duration positive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return duration;
    }

    private static boolean anyAlive(Thread... threads) {
        for (Thread thread : threads) if (thread.isAlive()) return true;
        return false;
    }

    private static Thread firstAlive(Thread... threads) {
        for (Thread thread : threads) if (thread.isAlive()) return thread;
        throw new IllegalStateException("No drain thread is alive.");
    }

    private static void boundedJoin(Thread thread, long nanoseconds) throws InterruptedException {
        if (nanoseconds > 0) thread.join(Duration.ofNanos(nanoseconds));
    }

    private static long deadline(long start, Duration duration) {
        long nanos;
        try {
            nanos = duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
        if (nanos > 0 && start > Long.MAX_VALUE - nanos) return Long.MAX_VALUE;
        return start + nanos;
    }

    private static long remaining(long deadline) {
        return deadline - System.nanoTime();
    }

    private static long earlier(long first, long second) {
        return remaining(first) <= remaining(second) ? first : second;
    }

    private static void close(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Cleanup remains bounded and source-free.
        }
    }

    private static BridgeTransportException failure() {
        return new BridgeTransportException("CodeDefense repository insights are unavailable.");
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command, Path directory, Map<String, String> environment) throws IOException;
    }

    private static final class ProcessTree {
        private final ProcessHandle root;
        private final Set<ProcessHandle> known = new LinkedHashSet<>();

        private ProcessTree(ProcessHandle root) {
            this.root = root;
            known.add(root);
            capture();
        }

        private void capture() {
            for (ProcessHandle handle : List.copyOf(known)) {
                try {
                    handle.descendants().forEach(known::add);
                } catch (RuntimeException ignored) {
                    // Cleanup remains best-effort and bounded.
                }
            }
            try {
                root.descendants().forEach(known::add);
            } catch (RuntimeException ignored) {
                // Cleanup remains best-effort and bounded.
            }
        }

        private List<ProcessHandle> handles() {
            List<ProcessHandle> handles = new ArrayList<>(known);
            handles.sort((left, right) -> {
                if (left.equals(right)) return 0;
                if (left.equals(root)) return 1;
                if (right.equals(root)) return -1;
                return 0;
            });
            return handles;
        }

        private boolean anyAlive() {
            for (ProcessHandle handle : known) if (handle.isAlive()) return true;
            return false;
        }
    }
}
