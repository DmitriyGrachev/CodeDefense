package dev.codedefense.analysis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class StagedChangeSchemaLoader {
    private static final String RESOURCE_PATH = "schemas/staged-change-analysis.schema.json";
    private static final int MAXIMUM_RESOURCE_BYTES = 256 * 1024;
    private final ClassLoader classLoader; private final ObjectMapper mapper = new ObjectMapper(); private volatile String cached;
    public StagedChangeSchemaLoader() { this(StagedChangeSchemaLoader.class.getClassLoader()); }
    StagedChangeSchemaLoader(ClassLoader classLoader) { this.classLoader = Objects.requireNonNull(classLoader); }
    public String load() {
        String value = cached; if (value != null) return value;
        synchronized (this) { if (cached == null) cached = loadAndValidate(); return cached; }
    }
    private String loadAndValidate() {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) throw unavailable(); ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int read;
            while ((read = input.read(b)) != -1) { if (output.size() + read > MAXIMUM_RESOURCE_BYTES) throw unavailable(); output.write(b, 0, read); }
            String schema = decode(output.toByteArray()); JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(schema);
            if (root == null || !root.isObject()) throw unavailable(); return schema;
        } catch (IOException | RuntimeException exception) { throw unavailable(); }
    }
    private static String decode(byte[] bytes) throws CharacterCodingException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Staged change analysis schema resource is unavailable."); }
}
