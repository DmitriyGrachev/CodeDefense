package dev.codedefense.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.AiProvider;
import dev.codedefense.ai.ReasoningEffort;
import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.StructuredCodexRequest;
import dev.codedefense.ai.StructuredCodexResult;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import java.util.Objects;

public final class AiProjectAnalyzer implements ProjectAnalyzer {
    private static final String INVALID_RESPONSE_MESSAGE = "Codex returned an invalid project analysis.";

    private final AiProvider aiProvider;
    private final ProjectAnalysisPromptFactory promptFactory;
    private final ProjectAnalysisSchemaLoader schemaLoader;
    private final ObjectMapper objectMapper;
    private final ProjectAnalysisValidator validator;
    private final CodexRuntimeConfig runtimeConfig;

    public AiProjectAnalyzer(AiProvider aiProvider) {
        this(aiProvider, new ProjectAnalysisPromptFactory(), new ProjectAnalysisSchemaLoader(),
                new ProjectAnalysisValidator(), new ObjectMapper(), CodexRuntimeConfig.defaults());
    }

    public AiProjectAnalyzer(
            AiProvider aiProvider,
            ProjectAnalysisPromptFactory promptFactory,
            ProjectAnalysisSchemaLoader schemaLoader,
            ProjectAnalysisValidator validator,
            ObjectMapper objectMapper,
            CodexRuntimeConfig runtimeConfig) {
        this.aiProvider = Objects.requireNonNull(aiProvider, "AI provider");
        this.promptFactory = Objects.requireNonNull(promptFactory, "Project analysis prompt factory");
        this.schemaLoader = Objects.requireNonNull(schemaLoader, "Project analysis schema loader");
        this.validator = Objects.requireNonNull(validator, "Project analysis validator");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper");
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "Codex runtime configuration");
    }

    @Override
    public ProjectAnalysis analyze(ProjectSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Project snapshot");
        StructuredCodexRequest request = createRequest(snapshot);
        StructuredCodexResult result = aiProvider.execute(request);
        return validator.validate(parse(result.finalJson()), snapshot);
    }

    private StructuredCodexRequest createRequest(ProjectSnapshot snapshot) {
        try {
            return new StructuredCodexRequest(
                    "project-analysis",
                    promptFactory.create(snapshot),
                    schemaLoader.load(),
                    runtimeConfig.defaultModel(),
                    ReasoningEffort.MEDIUM,
                    runtimeConfig.defaultExecutionTimeout());
        } catch (IllegalStateException exception) {
            throw new CodexExecutionException(-1, "Project analysis resources are unavailable.");
        }
    }

    private ProjectAnalysis parse(String finalJson) {
        try {
            return objectMapper.readerFor(ProjectAnalysis.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(finalJson);
        } catch (JsonProcessingException exception) {
            throw new InvalidCodexResponseException(INVALID_RESPONSE_MESSAGE);
        }
    }
}
