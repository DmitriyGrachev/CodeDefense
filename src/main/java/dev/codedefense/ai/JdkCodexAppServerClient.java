package dev.codedefense.ai;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.exception.CodexInterruptedException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded JSONL client for one explicitly selected app-server thread. */
public final class JdkCodexAppServerClient implements CodexAppServerClient {
    static final int MAXIMUM_LINE_BYTES = 1024 * 1024;
    static final int MAXIMUM_TOTAL_BYTES = 8 * 1024 * 1024;
    private static final Object EOF = new Object();

    private final Process process;
    private final OutputStream input;
    private final BlockingQueue<Object> lines = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private final AppServerProjectionCodec projectionCodec;
    private final long deadlineNanos;
    private final Duration gracePeriod;
    private final Set<Long> receivedIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicLong nextId = new AtomicLong();
    private boolean initialized;
    private boolean closed;

    public JdkCodexAppServerClient(CodexExecutable executable, Path workingDirectory,
            Map<String, String> environment, Duration timeout, Duration gracePeriod) {
        this(executable, workingDirectory, environment, timeout, gracePeriod,
                new AppServerProjectionCodec());
    }

    JdkCodexAppServerClient(CodexExecutable executable, Path workingDirectory,
            Map<String, String> environment, Duration timeout, Duration gracePeriod,
            AppServerProjectionCodec projectionCodec) {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(environment, "environment");
        requirePositive(timeout, "timeout");
        requirePositive(gracePeriod, "gracePeriod");
        if (!Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("workingDirectory must be an existing directory");
        }
        this.projectionCodec = Objects.requireNonNull(projectionCodec, "projectionCodec");
        this.gracePeriod = gracePeriod;
        this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        try {
            List<String> command = new ArrayList<>(executable.commandPrefix());
            command.add("app-server");
            command.add("--listen");
            command.add("stdio://");
            ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
            builder.environment().clear();
            builder.environment().putAll(Map.copyOf(environment));
            process = builder.start();
            input = process.getOutputStream();
            Thread.ofVirtual().name("codedefense-app-server-stdout").start(this::drainStdout);
            Thread.ofVirtual().name("codedefense-app-server-stderr").start(this::drainStderr);
        } catch (IOException exception) {
            throw new AppServerProtocolException(AppServerProtocolException.Kind.EXECUTION_FAILED, exception);
        }
    }

    @Override public synchronized void initialize(AppServerClientInfo clientInfo, boolean experimentalApi) {
        requireOpen();
        Objects.requireNonNull(clientInfo, "clientInfo");
        if (initialized) throw new IllegalStateException("app-server is already initialized");
        if (!lines.isEmpty()) throw invalid();
        call("initialize", Map.of(
                "clientInfo", Map.of("name", clientInfo.name(), "title", clientInfo.title(),
                        "version", clientInfo.version()),
                "capabilities", Map.of("experimentalApi", experimentalApi)));
        send(new AppServerRequest(null, "initialized", Map.of()));
        initialized = true;
    }

    @Override public synchronized AppServerThread readThread(String threadId, boolean includeTurns) {
        requireInitialized();
        AppServerResponse response = call("thread/read", Map.of(
                "threadId", requireThreadId(threadId), "includeTurns", includeTurns));
        return projectionCodec.decodeThread(response.json());
    }

    @Override public synchronized List<AppServerThreadItem> listThreadItems(String threadId, int limit) {
        requireInitialized();
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("item limit must be between 1 and 1000");
        }
        String selectedThread = requireThreadId(threadId);
        List<AppServerThreadItem> result = new ArrayList<>();
        String cursor = null;
        do {
            int remaining = limit - result.size();
            Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("threadId", selectedThread);
            params.put("limit", remaining);
            if (cursor != null) params.put("cursor", cursor);
            AppServerResponse response = call("thread/items/list", params);
            AppServerProjectionCodec.ItemPage page = projectionCodec.decodeItemPage(response.json());
            if (page.items().size() > remaining) throw invalid();
            result.addAll(page.items());
            cursor = page.nextCursor();
            if (cursor != null && (cursor.isBlank() || cursor.length() > 1024
                    || cursor.chars().anyMatch(Character::isISOControl))) throw invalid();
        } while (cursor != null && result.size() < limit);
        return List.copyOf(result);
    }

    private AppServerResponse call(String method, Map<String, Object> params) {
        long id = nextId.incrementAndGet();
        send(new AppServerRequest(id, method, params));
        while (true) {
            Object message = take();
            if (message == EOF) throw terminalFailure();
            if (message instanceof RuntimeException exception) throw exception;
            AppServerResponse response = envelope((byte[]) message);
            if (response == null) continue;
            if (!receivedIds.add(response.id()) || response.id() != id) throw invalid();
            if (response.errorCode() != null) {
                if (response.errorCode() == -32601) {
                    throw new AppServerProtocolException(AppServerProtocolException.Kind.UNSUPPORTED_METHOD);
                }
                throw invalid();
            }
            return response;
        }
    }

    private void send(AppServerRequest request) {
        try {
            Map<String, Object> value = request.id() == null
                    ? Map.of("method", request.method(), "params", request.params())
                    : Map.of("id", request.id(), "method", request.method(), "params", request.params());
            byte[] encoded = mapper.writeValueAsBytes(value);
            if (encoded.length > MAXIMUM_LINE_BYTES) {
                throw new AppServerProtocolException(AppServerProtocolException.Kind.LIMIT_EXCEEDED);
            }
            input.write(encoded);
            input.write('\n');
            input.flush();
        } catch (IOException exception) {
            throw new AppServerProtocolException(AppServerProtocolException.Kind.EXECUTION_FAILED, exception);
        }
    }

    private AppServerResponse envelope(byte[] line) {
        try (JsonParser parser = mapper.getFactory().createParser(line)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
            Long id = null;
            Integer error = null;
            boolean notification = false;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (field.equals("id") && value != JsonToken.VALUE_NULL) id = parser.getLongValue();
                else if (field.equals("method") && value == JsonToken.VALUE_STRING) notification = true;
                else if (field.equals("error") && value == JsonToken.START_OBJECT) error = readErrorCode(parser);
                else parser.skipChildren();
            }
            if (parser.nextToken() != null) throw invalid();
            if (id == null && notification) return null;
            if (id == null) throw invalid();
            return new AppServerResponse(id, error, line);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AppServerProtocolException protocol) throw protocol;
            throw invalid();
        }
    }

    private static Integer readErrorCode(JsonParser parser) throws IOException {
        Integer code = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if (field.equals("code") && value.isNumeric()) code = parser.getIntValue();
            else parser.skipChildren();
        }
        return code == null ? 0 : code;
    }

    private Object take() {
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) throw timeout();
            Object value = lines.poll(remaining, TimeUnit.NANOSECONDS);
            if (value == null) throw timeout();
            return value;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            close();
            throw new CodexInterruptedException(exception);
        }
    }

    private AppServerProtocolException timeout() {
        close();
        return new AppServerProtocolException(AppServerProtocolException.Kind.TIMEOUT);
    }

    private AppServerProtocolException terminalFailure() {
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining > 0) process.waitFor(remaining, TimeUnit.NANOSECONDS);
            return new AppServerProtocolException(process.isAlive() || process.exitValue() == 0
                    ? AppServerProtocolException.Kind.EOF
                    : AppServerProtocolException.Kind.EXECUTION_FAILED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            close();
            throw new CodexInterruptedException(exception);
        }
    }

    private void drainStdout() {
        int total = 0;
        try (InputStream output = process.getInputStream()) {
            while (true) {
                byte[] line = readLine(output);
                if (line == null) {
                    lines.offer(EOF);
                    return;
                }
                total = Math.addExact(total, line.length + 1);
                if (total > MAXIMUM_TOTAL_BYTES) {
                    throw new AppServerProtocolException(AppServerProtocolException.Kind.LIMIT_EXCEEDED);
                }
                decodeStrict(line);
                lines.put(line);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | IOException exception) {
            lines.offer(exception instanceof AppServerProtocolException protocol ? protocol : invalid());
        }
    }

    private static byte[] readLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) return line.size() == 0 ? null : line.toByteArray();
            if (value == '\n') return line.toByteArray();
            if (line.size() >= MAXIMUM_LINE_BYTES) {
                throw new AppServerProtocolException(AppServerProtocolException.Kind.LIMIT_EXCEEDED);
            }
            line.write(value);
        }
    }

    private static void decodeStrict(byte[] value) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value));
        } catch (CharacterCodingException exception) {
            throw invalid();
        }
    }

    private void drainStderr() {
        try (InputStream error = process.getErrorStream()) {
            byte[] buffer = new byte[8192];
            while (error.read(buffer) >= 0) { /* drain without retention */ }
        } catch (IOException ignored) { /* stdout/EOF governs the safe public outcome */ }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { input.close(); } catch (IOException ignored) { }
        terminate(false);
    }

    private void terminate(boolean forcibly) {
        List<ProcessHandle> descendants = process.descendants().toList();
        if (forcibly) descendants.reversed().forEach(ProcessHandle::destroyForcibly);
        else descendants.reversed().forEach(ProcessHandle::destroy);
        if (forcibly) process.destroyForcibly(); else process.destroy();
        try {
            if (!process.waitFor(gracePeriod.toMillis(), TimeUnit.MILLISECONDS) && !forcibly) terminate(true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void requireInitialized() {
        requireOpen();
        if (!initialized) throw new IllegalStateException("initialize must be called first");
    }
    private void requireOpen() {
        if (closed) throw new IllegalStateException("app-server is closed");
    }
    private static String requireThreadId(String value) {
        Objects.requireNonNull(value, "threadId");
        if (value.isBlank() || value.length() > 512 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("thread ID is invalid");
        }
        return value;
    }
    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
    private static AppServerProtocolException invalid() {
        return new AppServerProtocolException(AppServerProtocolException.Kind.INVALID_RESPONSE);
    }
    @Override public String toString() {
        return "JdkCodexAppServerClient[initialized=%s]".formatted(initialized);
    }
}
