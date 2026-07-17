package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReportNarrativeSchemaLoaderTest {
    @Test void rejectsMissingAndTrailingSchemaResourcesWithSafeMessage() {
        for (byte[] bytes : new byte[][] {null, "{} {}".getBytes(StandardCharsets.UTF_8), "[]".getBytes(StandardCharsets.UTF_8)}) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ReportNarrativeSchemaLoader(loader(bytes)).load());
            assertEquals("Report narrative schema resource is unavailable.", error.getMessage());
        }
    }
    @Test void rejectsMalformedUtf8OversizedAndUnsupportedKeywordsWithoutLeakingResourceContent() {
        for (byte[] bytes : new byte[][] { new byte[] {'{', (byte) 0xC3, (byte) 0x28, '}'}, ("{\"secret\":\"" + "x".repeat(300_000) + "\"}").getBytes(StandardCharsets.UTF_8), "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[],\"properties\":{},\"oneOf\":[]}".getBytes(StandardCharsets.UTF_8) }) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> new ReportNarrativeSchemaLoader(loader(bytes)).load());
            assertEquals("Report narrative schema resource is unavailable.", error.getMessage()); assertFalse(error.getMessage().contains("secret"));
        }
    }
    private static ClassLoader loader(byte[] bytes) { return new ClassLoader(null) { @Override public InputStream getResourceAsStream(String name) { return bytes == null ? null : new ByteArrayInputStream(bytes); } }; }
}
