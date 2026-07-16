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

public final class ProjectAnalysisSchemaLoader {
    private static final String RESOURCE_PATH = "schemas/project-analysis.schema.json";
    private static final String UNAVAILABLE_MESSAGE = "Project analysis schema resource is unavailable.";
    private static final int MAXIMUM_RESOURCE_BYTES = 256 * 1024;

    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String cached;

    public ProjectAnalysisSchemaLoader() {
        this(ProjectAnalysisSchemaLoader.class.getClassLoader());
    }

    ProjectAnalysisSchemaLoader(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "Class loader");
    }

    public String load() {
        String loaded = cached;
        if (loaded != null) {
            return loaded;
        }
        synchronized (this) {
            if (cached == null) {
                cached = loadAndValidate();
            }
            return cached;
        }
    }

    private String loadAndValidate() {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw unavailable();
            }
            String schema = decodeUtf8(readBounded(input));
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(schema);
            if (root == null || !root.isObject()) {
                throw unavailable();
            }
            return schema;
        } catch (IOException | RuntimeException exception) {
            throw unavailable();
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAXIMUM_RESOURCE_BYTES) {
                throw unavailable();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException(UNAVAILABLE_MESSAGE);
    }
}
