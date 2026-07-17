package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReportNarrativeSchemaTest {
    @Test void packagedSchemaHasStrictRequiredFiveFieldRootWithoutUnsupportedKeywords() throws Exception {
        JsonNode root = new ObjectMapper().readTree(new ReportNarrativeSchemaLoader().load());
        assertTrue(root.isObject()); assertEquals(false, root.path("additionalProperties").asBoolean(true));
        assertEquals(Set.of("headline", "summary", "strengths", "knowledgeGaps", "recommendedActions"), fields(root.path("properties")));
        assertEquals(5, root.path("required").size());
        for (String key : List.of("$ref", "oneOf", "anyOf", "allOf", "not", "if", "then", "else", "nullable", "uniqueItems")) assertFalse(root.toString().contains("\"" + key + "\""));
    }
    private static Set<String> fields(JsonNode node) { Set<String> names = new HashSet<>(); node.fieldNames().forEachRemaining(names::add); return names; }
}
