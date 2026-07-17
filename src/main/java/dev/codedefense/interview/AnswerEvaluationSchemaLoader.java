package dev.codedefense.interview;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Objects;

public final class AnswerEvaluationSchemaLoader {
    private static final String RESOURCE = "schemas/answer-evaluation.schema.json";
    private static final String MESSAGE = "Answer evaluation schema resource is unavailable.";
    private static final int MAX_BYTES = 256 * 1024;
    private final ClassLoader classLoader;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String cached;
    public AnswerEvaluationSchemaLoader() { this(AnswerEvaluationSchemaLoader.class.getClassLoader()); }
    AnswerEvaluationSchemaLoader(ClassLoader classLoader) { this.classLoader = Objects.requireNonNull(classLoader); }
    public String load() {
        String value = cached; if (value != null) return value;
        synchronized (this) { if (cached == null) cached = loadValidated(); return cached; }
    }
    private String loadValidated() {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) throw unavailable();
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) { if (output.size() + read > MAX_BYTES) throw unavailable(); output.write(buffer, 0, read); }
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
                    .replace("\r\n", "\n").replace('\r', '\n');
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
            if (root == null || !root.isObject()) throw unavailable();
            return value;
        } catch (IOException | RuntimeException exception) { throw unavailable(); }
    }
    private static IllegalStateException unavailable() { return new IllegalStateException(MESSAGE); }
}
