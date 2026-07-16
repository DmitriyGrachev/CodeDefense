package dev.codedefense.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.AiProvider;
import dev.codedefense.ai.ReasoningEffort;
import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.StructuredCodexRequest;
import dev.codedefense.ai.StructuredCodexResult;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.TechnicalQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiProjectAnalyzerTest {
    @Test
    void loadsThePackagedSchemaAsUnmodifiedUtf8Json() throws Exception {
        String schema = new ProjectAnalysisSchemaLoader().load();

        assertEquals(resourceText("schemas/project-analysis.schema.json"), schema);
        assertTrue(new ObjectMapper().readTree(schema).isObject());
    }

    @Test
    void sendsOneConfiguredRequestAndReturnsTheValidAnalysis() throws Exception {
        ProjectSnapshot snapshot = snapshot("private-snapshot-content");
        ProjectAnalysis expected = validAnalysis();
        CapturingAiProvider provider = new CapturingAiProvider(new StructuredCodexResult(
                new ObjectMapper().writeValueAsString(expected), Duration.ofMillis(10), "gpt-5.6-terra"));

        ProjectAnalysis actual = new AiProjectAnalyzer(provider).analyze(snapshot);

        assertEquals(expected, actual);
        assertEquals(1, provider.callCount());
        StructuredCodexRequest request = provider.request();
        assertNotNull(request);
        assertEquals("project-analysis", request.operationName());
        assertEquals("gpt-5.6-terra", request.model());
        assertEquals(ReasoningEffort.MEDIUM, request.reasoningEffort());
        assertEquals(Duration.ofSeconds(180), request.timeout());
        assertTrue(request.prompt().contains("private-snapshot-content"));
        assertTrue(new ObjectMapper().readTree(request.schemaJson()).isObject());
    }

    @Test
    void rejectsTrailingJsonWithAFixedMessageThatLeaksNoResponseOrRequestContent() throws Exception {
        String privateSnapshotContent = "private-snapshot-content";
        String privateResponseFragment = "private-model-response-fragment";
        String finalJson = new ObjectMapper().writeValueAsString(validAnalysis())
                + " {\"private\":\"" + privateResponseFragment + "\"}";
        CapturingAiProvider provider = new CapturingAiProvider(
                new StructuredCodexResult(finalJson, Duration.ZERO, "gpt-5.6-terra"));

        InvalidCodexResponseException exception = assertThrows(
                InvalidCodexResponseException.class,
                () -> new AiProjectAnalyzer(provider).analyze(snapshot(privateSnapshotContent)));

        assertEquals("Codex returned an invalid project analysis.", exception.getMessage());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(privateResponseFragment));
        assertFalse(exception.getMessage().contains(privateSnapshotContent));
        assertFalse(exception.getMessage().contains("repository-specific"));
        assertFalse(exception.getMessage().contains("\"maxLength\": 1200"));
    }

    @Test
    void propagatesSemanticValidationFailureForEvidenceOutsideTheSnapshot() throws Exception {
        ProjectAnalysis invalidAnalysis = analysisWithEvidencePath("src/Hallucinated.java");
        CapturingAiProvider provider = new CapturingAiProvider(new StructuredCodexResult(
                new ObjectMapper().writeValueAsString(invalidAnalysis), Duration.ZERO, "gpt-5.6-terra"));

        InvalidCodexResponseException exception = assertThrows(
                InvalidCodexResponseException.class,
                () -> new AiProjectAnalyzer(provider).analyze(snapshot("private-snapshot-content")));

        assertEquals("Codex returned an invalid project analysis.", exception.getMessage());
        assertEquals(1, provider.callCount());
    }

    @Test
    void preservesTheExistingCodexExceptionTypeAndInstance() {
        CodexNotInstalledException expected = new CodexNotInstalledException();

        CodexNotInstalledException actual = assertThrows(
                CodexNotInstalledException.class,
                () -> new AiProjectAnalyzer(request -> {
                    throw expected;
                }).analyze(snapshot("private-snapshot-content")));

        assertSame(expected, actual);
    }

    @Test
    void rejectsMalformedAndMissingFieldsWithTheSameSafeMessage() throws Exception {
        String missingQuestions = new ObjectMapper().writeValueAsString(validAnalysis()).replaceFirst(
                ",\"questions\":\\[.*", "}");
        for (String json : List.of("not-json-private-value", "{}", missingQuestions)) {
            CapturingAiProvider provider = new CapturingAiProvider(
                    new StructuredCodexResult(json, Duration.ZERO, "gpt-5.6-terra"));

            InvalidCodexResponseException exception = assertThrows(
                    InvalidCodexResponseException.class,
                    () -> new AiProjectAnalyzer(provider).analyze(snapshot("private-snapshot-content")));

            assertEquals("Codex returned an invalid project analysis.", exception.getMessage());
            assertFalse(exception.getMessage().contains(json));
            assertEquals(1, provider.callCount());
        }
    }

    @Test
    void productionRuntimeFactoryBuildsALazyAnalyzerWithoutCallingCodex() {
        assertNotNull(new ProjectAnalysisRuntimeFactory().create());
    }

    @Test
    void mapsMissingPromptResourceToSafeExecutionFailureWithoutCallingProvider() throws Exception {
        CapturingAiProvider provider = providerWithValidResult();
        AiProjectAnalyzer analyzer = analyzer(
                provider,
                new ProjectAnalysisPromptFactory(new ClassLoader(null) {}),
                new ProjectAnalysisSchemaLoader());

        assertUnavailableResources(analyzer, provider);
    }

    @Test
    void mapsMissingSchemaResourceToSafeExecutionFailureWithoutCallingProvider() throws Exception {
        CapturingAiProvider provider = providerWithValidResult();
        AiProjectAnalyzer analyzer = analyzer(
                provider,
                new ProjectAnalysisPromptFactory(),
                new ProjectAnalysisSchemaLoader(new ClassLoader(null) {}));

        assertUnavailableResources(analyzer, provider);
    }

    private static void assertUnavailableResources(AiProjectAnalyzer analyzer, CapturingAiProvider provider) {
        CodexExecutionException exception = assertThrows(
                CodexExecutionException.class,
                () -> analyzer.analyze(snapshot("private-snapshot-content")));

        assertEquals("Codex execution failed with exit code -1.\nProject analysis resources are unavailable.",
                exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(0, provider.callCount());
        assertFalse(exception.getMessage().contains("private-snapshot-content"));
        assertFalse(exception.getMessage().contains("prompt resource"));
        assertFalse(exception.getMessage().contains("schema resource"));
    }

    private static AiProjectAnalyzer analyzer(
            AiProvider provider,
            ProjectAnalysisPromptFactory promptFactory,
            ProjectAnalysisSchemaLoader schemaLoader) {
        return new AiProjectAnalyzer(
                provider,
                promptFactory,
                schemaLoader,
                new ProjectAnalysisValidator(),
                new ObjectMapper(),
                CodexRuntimeConfig.defaults());
    }

    private static CapturingAiProvider providerWithValidResult() throws Exception {
        return new CapturingAiProvider(new StructuredCodexResult(
                new ObjectMapper().writeValueAsString(validAnalysis()), Duration.ZERO, "gpt-5.6-terra"));
    }

    private static String resourceText(String path) throws Exception {
        try (InputStream input = AiProjectAnalyzerTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ProjectAnalysis validAnalysis() {
        return new ProjectAnalysis(
                "fixture-project",
                "Java CLI",
                "A fixture application for analyzer tests.",
                List.of("The command invokes the application service.", "The service validates supplied input."),
                List.of(new ProjectComponent(
                        "Application",
                        "entry-point",
                        "Starts the fixture application.",
                        List.of("src/App.java"))),
                List.of("startup", "validation"),
                List.of(
                        question("startup", "How does the command start?", "src/App.java", 1, 2),
                        question("flow", "How does the service flow work?", "src/Service.java", 1, 2),
                        question("safety", "How does the application validate input?", "src/App.java", 3, 4)));
    }

    private static ProjectAnalysis analysisWithEvidencePath(String evidencePath) {
        ProjectAnalysis base = validAnalysis();
        return new ProjectAnalysis(
                base.projectName(),
                base.projectType(),
                base.summary(),
                base.mainFlow(),
                base.components(),
                base.criticalTopics(),
                List.of(
                        base.questions().get(0),
                        base.questions().get(1),
                        question("safety", "How does the application validate input?", evidencePath, 1, 1)));
    }

    private static TechnicalQuestion question(String id, String prompt, String path, int startLine, int endLine) {
        return new TechnicalQuestion(
                id,
                prompt,
                "Understand the fixture implementation.",
                List.of("First key point", "Second key point"),
                List.of(new CodeEvidence(path, startLine, endLine, "Relevant fixture code.")));
    }

    private static ProjectSnapshot snapshot(String promptContent) {
        Path root = Path.of("fixture-project");
        return new ProjectSnapshot(
                root,
                "fixture-project",
                "Java CLI",
                new ScanSummary(root, 2, 0, List.of(
                        new SourceFile(Path.of("src", "App.java")),
                        new SourceFile(Path.of("src", "Service.java")))),
                List.of(
                        new ProjectSnapshot.SelectedFile(Path.of("src", "App.java"), 4, false, 100),
                        new ProjectSnapshot.SelectedFile(Path.of("src", "Service.java"), 2, false, 100)),
                promptContent,
                promptContent.getBytes(StandardCharsets.UTF_8).length,
                0);
    }

    private static final class CapturingAiProvider implements AiProvider {
        private final StructuredCodexResult result;
        private final AtomicInteger callCount = new AtomicInteger();
        private final AtomicReference<StructuredCodexRequest> request = new AtomicReference<>();

        private CapturingAiProvider(StructuredCodexResult result) {
            this.result = result;
        }

        @Override
        public StructuredCodexResult execute(StructuredCodexRequest request) {
            callCount.incrementAndGet();
            this.request.set(request);
            return result;
        }

        private int callCount() {
            return callCount.get();
        }

        private StructuredCodexRequest request() {
            return request.get();
        }
    }
}
