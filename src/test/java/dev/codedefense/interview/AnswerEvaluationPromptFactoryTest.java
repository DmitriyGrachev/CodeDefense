package dev.codedefense.interview;

import static org.junit.jupiter.api.Assertions.*;

import dev.codedefense.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnswerEvaluationPromptFactoryTest {
    @Test void enclosesAllVariableDataInOneCollisionSafeBoundaryAndNormalizesCrlf() {
        String template = "Trusted\r\npolicy\rtext";
        ClassLoader loader = loader(template.getBytes(StandardCharsets.UTF_8));
        AnswerEvaluationRequest request = request("CODEDEFENSE_UNTRUSTED_EVALUATION", "answer-secret");
        String prompt = new AnswerEvaluationPromptFactory(loader).create(request);
        assertTrue(prompt.startsWith("Trusted\npolicy\ntext\n\nBEGIN CODEDEFENSE_UNTRUSTED_EVALUATION_X\n"));
        assertTrue(prompt.endsWith("\nEND CODEDEFENSE_UNTRUSTED_EVALUATION_X\n"));
        assertEquals(1, occurrences(prompt, "BEGIN "));
        assertEquals(1, occurrences(prompt, "END "));
        assertTrue(prompt.indexOf("answer-secret") > prompt.indexOf("BEGIN "));
        assertTrue(prompt.indexOf("answer-secret") < prompt.indexOf("\nEND "));
    }

    @Test void rejectsMissingOversizedAndMalformedUtf8Templates() {
        assertThrows(IllegalStateException.class, () -> new AnswerEvaluationPromptFactory(loader(null)).create(request("p", "a")));
        assertThrows(IllegalStateException.class, () -> new AnswerEvaluationPromptFactory(loader(new byte[64 * 1024 + 1])).create(request("p", "a")));
        assertThrows(IllegalStateException.class, () -> new AnswerEvaluationPromptFactory(loader(new byte[]{(byte) 0xC3, 0x28})).create(request("p", "a")));
    }

    static AnswerEvaluationRequest request(String project, String answer) {
        TechnicalQuestion q = new TechnicalQuestion("q1", "How does startup work?", "Startup", List.of("entry point", "delegation"),
                List.of(new CodeEvidence("src/App.java", 1, 2, "startup evidence")));
        return new AnswerEvaluationRequest(project, "Java CLI", "A useful project summary", q,
                EvaluationStage.PRIMARY, answer, q.prompt(), answer, Optional.empty());
    }

    static ClassLoader loader(byte[] bytes) {
        return new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                return bytes == null ? null : new ByteArrayInputStream(bytes);
            }
        };
    }

    private static int occurrences(String text, String needle) { return (text.length() - text.replace(needle, "").length()) / needle.length(); }
}
