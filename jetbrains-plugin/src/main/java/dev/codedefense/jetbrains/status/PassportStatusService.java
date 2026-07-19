package dev.codedefense.jetbrains.status;

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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class PassportStatusService {
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final Path cliJar;
    private final CommandRunner runner;
    private final Duration timeout;

    public PassportStatusService(Path cliJar, CommandRunner runner, Duration timeout) {
        this.cliJar = cliJar.toAbsolutePath().normalize();
        this.runner = java.util.Objects.requireNonNull(runner, "runner");
        this.timeout = java.util.Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
    }

    public static PassportStatusService production(Path cliJar) {
        Path java = new JavaExecutableResolver().resolve(Path.of(System.getProperty("java.home")));
        return new PassportStatusService(cliJar, new JdkCommandRunner(java), Duration.ofSeconds(15));
    }

    public PassportStatusView refresh(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<String> command = List.of(javaCommand(), "-jar", cliJar.toString(), "passport", "show",
                root.toString(), "--format", "json");
        CommandResult result = runner.run(command, root, timeout);
        if (result.exitCode() != 0) throw failure();
        return parse(result.stdout());
    }

    private String javaCommand() {
        return runner instanceof JdkCommandRunner jdk ? jdk.javaExecutable.toString() : "java";
    }

    private PassportStatusView parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_OUTPUT_BYTES) throw failure();
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw failure(exception);
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) throw failure();
            int protocol = integer(root, "protocolVersion");
            boolean present = bool(root, "present");
            if (protocol != 1) throw failure();
            if (!present) {
                fields(root, "protocolVersion", "present");
                return PassportStatusView.absent();
            }
            fields(root, "protocolVersion", "present", "status", "changeKind", "shortFingerprint",
                    "focus", "attemptNumber", "overallScore", "readiness", "categories");
            List<PassportStatusView.CategoryScore> categories = new ArrayList<>();
            JsonNode categoryNodes = root.get("categories");
            if (categoryNodes == null || !categoryNodes.isArray()) throw failure();
            for (JsonNode category : categoryNodes) {
                fields(category, "id", "score");
                categories.add(new PassportStatusView.CategoryScore(text(category, "id"),
                        integer(category, "score")));
            }
            return new PassportStatusView(true, text(root, "status"), text(root, "changeKind"),
                    text(root, "shortFingerprint"), text(root, "focus"), integer(root, "attemptNumber"),
                    integer(root, "overallScore"), text(root, "readiness"), categories);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw failure(exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().length() > 256) throw failure();
        return value.textValue();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw failure();
        return value.intValue();
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) throw failure();
        return value.booleanValue();
    }

    private static void fields(JsonNode node, String... expected) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(expected))) throw failure();
    }

    private static BridgeTransportException failure() {
        return new BridgeTransportException("CodeDefense Passport status is unavailable.");
    }

    private static BridgeTransportException failure(Throwable cause) {
        return new BridgeTransportException("CodeDefense Passport status is unavailable.", cause);
    }

    interface CommandRunner {
        CommandResult run(List<String> command, Path workingDirectory, Duration timeout);
    }

    record CommandResult(int exitCode, byte[] stdout) {
        CommandResult {
            stdout = stdout.clone();
        }
        @Override public byte[] stdout() { return stdout.clone(); }
    }

    private static final class JdkCommandRunner implements CommandRunner {
        private final Path javaExecutable;
        private JdkCommandRunner(Path javaExecutable) { this.javaExecutable = javaExecutable; }

        @Override public CommandResult run(List<String> command, Path workingDirectory, Duration timeout) {
            try {
                Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
                var stdout = new ByteArrayOutputStream();
                var overflow = new boolean[1];
                Thread out = Thread.ofVirtual().start(() -> drain(process.getInputStream(), stdout, overflow));
                Thread err = Thread.ofVirtual().start(() -> drain(process.getErrorStream(), null, null));
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroy();
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
                    throw failure();
                }
                out.join(); err.join();
                if (overflow[0]) throw failure();
                return new CommandResult(process.exitValue(), stdout.toByteArray());
            } catch (IOException exception) {
                throw failure(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw failure(exception);
            }
        }

        private static void drain(InputStream input, ByteArrayOutputStream capture, boolean[] overflow) {
            try (input) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (capture != null && capture.size() < MAX_OUTPUT_BYTES) {
                        int accepted = Math.min(read, MAX_OUTPUT_BYTES - capture.size());
                        capture.write(buffer, 0, accepted);
                        if (accepted < read) overflow[0] = true;
                    } else if (capture != null && read > 0) overflow[0] = true;
                }
            } catch (IOException exception) {
                throw failure(exception);
            }
        }
    }
}
