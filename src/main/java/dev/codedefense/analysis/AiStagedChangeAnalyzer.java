package dev.codedefense.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.AiProvider;
import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.ReasoningEffort;
import dev.codedefense.ai.StructuredCodexRequest;
import dev.codedefense.ai.StructuredCodexResult;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.DefenseFocus;
import java.util.Objects;

public final class AiStagedChangeAnalyzer implements StagedChangeAnalyzer {
    private static final String INVALID_RESPONSE = "Codex returned an invalid staged change analysis.";
    private final AiProvider aiProvider; private final StagedChangePromptFactory promptFactory;
    private final StagedChangeSchemaLoader schemaLoader; private final StagedChangeAnalysisValidator validator;
    private final ObjectMapper mapper; private final CodexRuntimeConfig config;
    public AiStagedChangeAnalyzer(AiProvider aiProvider) { this(aiProvider, new StagedChangePromptFactory(), new StagedChangeSchemaLoader(), new StagedChangeAnalysisValidator(), new ObjectMapper(), CodexRuntimeConfig.defaults()); }
    public AiStagedChangeAnalyzer(AiProvider aiProvider, StagedChangePromptFactory promptFactory, StagedChangeSchemaLoader schemaLoader, StagedChangeAnalysisValidator validator, ObjectMapper mapper, CodexRuntimeConfig config) {
        this.aiProvider=Objects.requireNonNull(aiProvider); this.promptFactory=Objects.requireNonNull(promptFactory); this.schemaLoader=Objects.requireNonNull(schemaLoader); this.validator=Objects.requireNonNull(validator); this.mapper=Objects.requireNonNull(mapper); this.config=Objects.requireNonNull(config);
    }
    @Override public ProjectAnalysis analyze(StagedChange change, ProjectSnapshot snapshot) {
        return analyze(change, snapshot, DefenseFocus.BALANCED);
    }
    @Override public ProjectAnalysis analyze(StagedChange change, ProjectSnapshot snapshot, DefenseFocus focus) {
        Objects.requireNonNull(change); Objects.requireNonNull(snapshot); StructuredCodexRequest request;
        try { request = new StructuredCodexRequest("staged-change-analysis", promptFactory.create(change, snapshot, focus), schemaLoader.load(), config.defaultModel(), ReasoningEffort.MEDIUM, config.defaultExecutionTimeout()); }
        catch (IllegalStateException exception) { throw new CodexExecutionException(-1, "Staged change analysis resources are unavailable."); }
        StructuredCodexResult result = aiProvider.execute(request); return validator.validate(parse(result == null ? null : result.finalJson()), snapshot);
    }
    private ProjectAnalysis parse(String json) {
        try { return mapper.readerFor(ProjectAnalysis.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json); }
        catch (JsonProcessingException | RuntimeException exception) { throw new InvalidCodexResponseException(INVALID_RESPONSE); }
    }
}
