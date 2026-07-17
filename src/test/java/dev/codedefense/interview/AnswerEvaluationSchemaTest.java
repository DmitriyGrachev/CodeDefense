package dev.codedefense.interview;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnswerEvaluationSchemaTest {
    @Test void bundledSchemaIsStrictAndUsesSupportedKeywords() throws Exception {
        String schema = new AnswerEvaluationSchemaLoader().load();
        JsonNode root = new ObjectMapper().readTree(schema);
        assertEquals("object", root.get("type").asText());
        assertFalse(root.get("additionalProperties").asBoolean());
        assertEquals(Set.of("verdict", "score", "feedback", "understoodConcepts", "missingConcepts", "followUpQuestion"),
                new HashSet<>(new ObjectMapper().convertValue(root.get("required"), List.class)));
        for (String forbidden : List.of("uniqueItems", "oneOf", "anyOf", "allOf", "$ref", "nullable", "if", "then", "else")) assertFalse(schema.contains("\"" + forbidden + "\""));
    }
}
