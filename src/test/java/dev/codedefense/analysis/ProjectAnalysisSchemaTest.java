package dev.codedefense.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAnalysisSchemaTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsStrictBoundedProjectAnalysisSchema() throws Exception {
        JsonNode schema;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("schemas/project-analysis.schema.json")) {
            assertNotNull(input, "project analysis schema must be packaged as a resource");
            schema = objectMapper.readTree(input);
        }

        assertStrictObject(schema, "projectName", "projectType", "summary", "mainFlow", "components", "criticalTopics", "questions");
        JsonNode properties = schema.path("properties");
        assertStringBounds(properties.path("projectName"), 1, 120);
        assertStringBounds(properties.path("projectType"), 1, 120);
        assertStringBounds(properties.path("summary"), 20, 1200);
        assertArrayBounds(properties.path("mainFlow"), 2, 8);
        assertTrue(properties.path("mainFlow").path("uniqueItems").asBoolean());
        assertStringBounds(properties.path("mainFlow").path("items"), 3, 300);
        assertArrayBounds(properties.path("components"), 1, 12);
        assertArrayBounds(properties.path("criticalTopics"), 2, 8);
        assertTrue(properties.path("criticalTopics").path("uniqueItems").asBoolean());
        assertStringBounds(properties.path("criticalTopics").path("items"), 2, 160);

        JsonNode component = properties.path("components").path("items");
        assertStrictObject(component, "name", "kind", "responsibility", "paths");
        assertStringBounds(component.path("properties").path("name"), 1, 120);
        assertStringBounds(component.path("properties").path("kind"), 1, 64);
        assertStringBounds(component.path("properties").path("responsibility"), 5, 500);
        assertArrayBounds(component.path("properties").path("paths"), 1, 5);
        assertTrue(component.path("properties").path("paths").path("uniqueItems").asBoolean());
        assertStringBounds(component.path("properties").path("paths").path("items"), 1, 300);

        JsonNode questions = properties.path("questions");
        assertArrayBounds(questions, 3, 3);
        JsonNode question = questions.path("items");
        assertStrictObject(question, "id", "prompt", "learningGoal", "expectedKeyPoints", "evidence");
        assertStringBounds(question.path("properties").path("id"), 1, 64);
        assertEquals("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$", question.path("properties").path("id").path("pattern").asText());
        assertStringBounds(question.path("properties").path("prompt"), 10, 500);
        assertStringBounds(question.path("properties").path("learningGoal"), 3, 240);
        JsonNode expectedKeyPoints = question.path("properties").path("expectedKeyPoints");
        assertBoundedArray(expectedKeyPoints);
        assertEquals(2, expectedKeyPoints.path("minItems").asInt());
        assertEquals(6, expectedKeyPoints.path("maxItems").asInt());
        assertTrue(expectedKeyPoints.path("uniqueItems").asBoolean());
        assertStringBounds(expectedKeyPoints.path("items"), 2, 300);

        JsonNode evidence = question.path("properties").path("evidence");
        assertBoundedArray(evidence);
        assertEquals(1, evidence.path("minItems").asInt());
        assertEquals(3, evidence.path("maxItems").asInt());
        JsonNode evidenceItem = evidence.path("items");
        assertStrictObject(evidenceItem, "path", "startLine", "endLine", "reason");
        assertStringBounds(evidenceItem.path("properties").path("path"), 1, 300);
        assertStringBounds(evidenceItem.path("properties").path("reason"), 3, 300);
        assertEquals("integer", evidenceItem.path("properties").path("startLine").path("type").asText());
        assertEquals("integer", evidenceItem.path("properties").path("endLine").path("type").asText());
    }

    private static void assertStrictObject(JsonNode object, String... fields) {
        assertTrue(object.isObject());
        assertEquals("object", object.path("type").asText());
        assertFalse(object.path("additionalProperties").asBoolean(true));
        List<String> required = new ArrayList<>();
        object.path("required").forEach(field -> required.add(field.asText()));
        assertTrue(required.containsAll(List.of(fields)));
    }

    private static void assertBoundedString(JsonNode node) {
        assertEquals("string", node.path("type").asText());
        assertTrue(node.path("minLength").canConvertToInt());
        assertTrue(node.path("maxLength").canConvertToInt());
        assertTrue(node.path("minLength").asInt() >= 1);
        assertTrue(node.path("maxLength").asInt() >= node.path("minLength").asInt());
    }

    private static void assertStringBounds(JsonNode node, int minimum, int maximum) {
        assertBoundedString(node);
        assertEquals(minimum, node.path("minLength").asInt());
        assertEquals(maximum, node.path("maxLength").asInt());
    }

    private static void assertBoundedArray(JsonNode node) {
        assertEquals("array", node.path("type").asText());
        assertTrue(node.path("minItems").canConvertToInt());
        assertTrue(node.path("maxItems").canConvertToInt());
        assertTrue(node.path("minItems").asInt() >= 0);
        assertTrue(node.path("maxItems").asInt() >= node.path("minItems").asInt());
    }

    private static void assertArrayBounds(JsonNode node, int minimum, int maximum) {
        assertBoundedArray(node);
        assertEquals(minimum, node.path("minItems").asInt());
        assertEquals(maximum, node.path("maxItems").asInt());
    }
}
