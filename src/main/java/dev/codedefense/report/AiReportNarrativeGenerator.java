package dev.codedefense.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import dev.codedefense.ai.*;
import dev.codedefense.ai.exception.*;
import dev.codedefense.domain.*;
import java.util.*;

public final class AiReportNarrativeGenerator implements ReportNarrativeGenerator {
    private final AiProvider provider; private final ReportNarrativePromptFactory promptFactory; private final ReportNarrativeSchemaLoader schemaLoader; private final ReportNarrativeValidator validator; private final ObjectMapper mapper; private final CodexRuntimeConfig runtimeConfig; private final ReportConfig reportConfig;
    public AiReportNarrativeGenerator(AiProvider provider){this(provider,new ReportNarrativePromptFactory(),new ReportNarrativeSchemaLoader(),new ReportNarrativeValidator(),new ObjectMapper(),CodexRuntimeConfig.defaults(),ReportConfig.defaults());}
    public AiReportNarrativeGenerator(AiProvider provider,ReportNarrativePromptFactory promptFactory,ReportNarrativeSchemaLoader schemaLoader,ReportNarrativeValidator validator,ObjectMapper mapper,CodexRuntimeConfig runtimeConfig,ReportConfig reportConfig){this.provider=Objects.requireNonNull(provider);this.promptFactory=Objects.requireNonNull(promptFactory);this.schemaLoader=Objects.requireNonNull(schemaLoader);this.validator=Objects.requireNonNull(validator);this.mapper=Objects.requireNonNull(mapper);this.runtimeConfig=Objects.requireNonNull(runtimeConfig);this.reportConfig=Objects.requireNonNull(reportConfig);}
    @Override public ReportNarrative generate(ReportGenerationRequest request, ReportMetadata metadata){Objects.requireNonNull(request);Objects.requireNonNull(metadata); StructuredCodexRequest structured=createRequest(request,metadata); StructuredCodexResult result=provider.execute(structured); return validator.validate(parse(result == null ? null : result.finalJson()));}
    private StructuredCodexRequest createRequest(ReportGenerationRequest request,ReportMetadata metadata){try{return new StructuredCodexRequest("report-narrative",promptFactory.create(request,metadata),schemaLoader.load(),runtimeConfig.defaultModel(),ReasoningEffort.LOW,reportConfig.narrativeTimeout());}catch(IllegalStateException e){throw new CodexExecutionException(-1,"Report narrative resources are unavailable.");}}
    private ReportNarrative parse(String json){try{return mapper.readerFor(ReportNarrative.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);}catch(JsonProcessingException|RuntimeException e){throw new InvalidCodexResponseException("Codex returned an invalid report narrative.");}}
}
