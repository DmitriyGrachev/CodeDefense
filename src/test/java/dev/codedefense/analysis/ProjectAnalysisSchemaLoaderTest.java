package dev.codedefense.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProjectAnalysisSchemaLoaderTest {
    @Test
    void loadsAndCachesThePackagedSchemaAsOneJsonObject() throws Exception {
        ProjectAnalysisSchemaLoader loader = new ProjectAnalysisSchemaLoader();

        String first = loader.load();
        String second = loader.load();
        var node = new ObjectMapper().reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(first);

        assertEquals(first, second);
        assertFalse(first.isBlank());
        assertEquals(true, node.isObject());
    }

    @Test
    void rejectsMissingNonObjectTrailingMalformedAndOversizedResourcesWithoutLeakingContent() {
        assertSafeFailure(new ProjectAnalysisSchemaLoader(resource(null)));
        assertSafeFailure(new ProjectAnalysisSchemaLoader(resource("[]".getBytes(StandardCharsets.UTF_8))));
        assertSafeFailure(new ProjectAnalysisSchemaLoader(resource("{} {}".getBytes(StandardCharsets.UTF_8))));
        assertSafeFailure(new ProjectAnalysisSchemaLoader(resource(new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'})));
        assertSafeFailure(new ProjectAnalysisSchemaLoader(resource(("{\"secret\":\"" + "x".repeat(300_000) + "\"}")
                .getBytes(StandardCharsets.UTF_8))));
    }

    private static void assertSafeFailure(ProjectAnalysisSchemaLoader loader) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, loader::load);
        assertEquals("Project analysis schema resource is unavailable.", exception.getMessage());
        assertFalse(exception.getMessage().contains("secret"));
    }

    private static ClassLoader resource(byte[] bytes) {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return bytes == null ? null : new ByteArrayInputStream(bytes);
            }
        };
    }
}
