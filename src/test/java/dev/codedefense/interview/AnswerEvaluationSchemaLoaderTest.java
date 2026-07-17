package dev.codedefense.interview;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AnswerEvaluationSchemaLoaderTest {
    @Test void loadsNormalizesAndCachesOneJsonObject() {
        byte[] bytes = "{\r\n\"type\":\"object\"\r}\n".getBytes(StandardCharsets.UTF_8);
        AnswerEvaluationSchemaLoader loader = new AnswerEvaluationSchemaLoader(resource(bytes));
        String first = loader.load();
        assertEquals("{\n\"type\":\"object\"\n}\n", first);
        assertSame(first, loader.load());
    }
    @Test void mapsMissingMalformedTrailingAndOversizedResourcesToSafeMessage() {
        for (byte[] bytes : new byte[][] {null, "[]".getBytes(StandardCharsets.UTF_8), "{} {}".getBytes(StandardCharsets.UTF_8), new byte[256 * 1024 + 1], {(byte)0xC3, 0x28}}) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> new AnswerEvaluationSchemaLoader(resource(bytes)).load());
            assertEquals("Answer evaluation schema resource is unavailable.", error.getMessage());
        }
    }
    private static ClassLoader resource(byte[] bytes) { return AnswerEvaluationPromptFactoryTest.loader(bytes); }
}
