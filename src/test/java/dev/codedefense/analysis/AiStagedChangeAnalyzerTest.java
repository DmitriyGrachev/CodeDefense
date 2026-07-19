package dev.codedefense.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import dev.codedefense.ai.AiProvider;
import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.ReasoningEffort;
import dev.codedefense.ai.StructuredCodexRequest;
import dev.codedefense.ai.StructuredCodexResult;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiStagedChangeAnalyzerTest {
    @Test
    void sendsConfiguredMediumStructuredRequestAndValidatesTypedQuestions() throws Exception {
        CapturingProvider provider = new CapturingProvider(new StructuredCodexResult(
                new ObjectMapper().writeValueAsString(StagedChangeAnalysisValidatorTest.analysis(
                        "decision", "counterfactual", "test-prediction")), Duration.ZERO, "fixture-model"));
        CodexRuntimeConfig config = new CodexRuntimeConfig("fixture-model", Duration.ofSeconds(1),
                Duration.ofSeconds(17), Duration.ofSeconds(1), 1024, 1024, 65536);

        String privateIndexContent = "private-index-content";
        ProjectSnapshot snapshot = snapshotWithContent(privateIndexContent);
        ProjectAnalysis analysis = new AiStagedChangeAnalyzer(provider, new StagedChangePromptFactory(),
                new StagedChangeSchemaLoader(), new StagedChangeAnalysisValidator(), new ObjectMapper(), config)
                .analyze(change(), snapshot);

        assertEquals(3, analysis.questions().size());
        StructuredCodexRequest request = provider.request.get();
        assertEquals("staged-change-analysis", request.operationName());
        assertEquals("fixture-model", request.model());
        assertEquals(ReasoningEffort.MEDIUM, request.reasoningEffort());
        assertEquals(Duration.ofSeconds(17), request.timeout());
        assertTrue(request.schemaJson().contains("additionalProperties"));
        assertFalse(request.toString().contains(privateIndexContent));
        assertFalse(request.toString().contains("staged-change-analysis.schema"));
    }

    @Test
    void trailingOrInvalidJsonUsesOneSafeErrorWithoutPrivateData() {
        String secret = "private-index-content";
        CapturingProvider provider = new CapturingProvider(new StructuredCodexResult("{} {\"x\":\"" + secret + "\"}",
                Duration.ZERO, "fixture-model"));

        InvalidCodexResponseException exception = assertThrows(InvalidCodexResponseException.class,
                () -> new AiStagedChangeAnalyzer(provider).analyze(change(), StagedChangeAnalysisValidatorTest.snapshot()));

        assertEquals("Codex returned an invalid staged change analysis.", exception.getMessage());
        assertFalse(exception.getMessage().contains(secret));
    }

    @Test
    void packagedSchemaIsStrictAndRequiresEveryProjectAnalysisField() throws Exception {
        JsonNode schema = new ObjectMapper().readTree(new StagedChangeSchemaLoader().load());

        assertTrue(schema.path("additionalProperties").isBoolean());
        assertFalse(schema.path("additionalProperties").asBoolean());
        assertEquals(List.of("projectName", "projectType", "summary", "mainFlow", "components", "criticalTopics", "questions"),
                java.util.stream.StreamSupport.stream(schema.path("required").spliterator(), false)
                        .map(JsonNode::asText).toList());
        assertEquals(List.of("decision", "counterfactual", "test-prediction"),
                java.util.stream.StreamSupport.stream(schema.path("properties").path("questions").path("items")
                        .path("properties").path("id").path("enum").spliterator(), false).map(JsonNode::asText).toList());
    }

    private static StagedChange change() {
        Path root = Path.of(".").toAbsolutePath().normalize();
        return new StagedChange(root, "a".repeat(64), "b".repeat(40), "c".repeat(64), "d".repeat(64),
                List.of(new StagedChangeFile(Path.of("src/App.java"), StagedFileStatus.MODIFIED, 2, 1)), 2, 1);
    }

    private static ProjectSnapshot snapshotWithContent(String content) {
        ProjectSnapshot base = StagedChangeAnalysisValidatorTest.snapshot();
        return new ProjectSnapshot(base.root(), base.projectName(), base.projectType(), base.scanSummary(),
                base.selectedFiles(), content, content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, 0);
    }

    private static final class CapturingProvider implements AiProvider {
        private final StructuredCodexResult result;
        private final AtomicReference<StructuredCodexRequest> request = new AtomicReference<>();
        private CapturingProvider(StructuredCodexResult result) { this.result = result; }
        @Override public StructuredCodexResult execute(StructuredCodexRequest structured) { request.set(structured); return result; }
    }
}
