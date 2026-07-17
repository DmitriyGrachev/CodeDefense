package dev.codedefense.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import dev.codedefense.ai.*;
import dev.codedefense.ai.exception.*;
import dev.codedefense.domain.*;
import java.time.Duration;
import java.util.Objects;

public final class AiAnswerEvaluator implements AnswerEvaluator {
    private static final String INVALID = "Codex returned an invalid answer evaluation.";
    private final AiProvider provider; private final AnswerEvaluationPromptFactory promptFactory;
    private final AnswerEvaluationSchemaLoader schemaLoader; private final AnswerEvaluationValidator validator;
    private final ObjectMapper mapper; private final CodexRuntimeConfig config;
    public AiAnswerEvaluator(AiProvider provider) { this(provider, new AnswerEvaluationPromptFactory(), new AnswerEvaluationSchemaLoader(), new AnswerEvaluationValidator(), new ObjectMapper(), CodexRuntimeConfig.defaults()); }
    public AiAnswerEvaluator(AiProvider provider, AnswerEvaluationPromptFactory promptFactory, AnswerEvaluationSchemaLoader schemaLoader,
            AnswerEvaluationValidator validator, ObjectMapper mapper, CodexRuntimeConfig config) {
        this.provider=Objects.requireNonNull(provider); this.promptFactory=Objects.requireNonNull(promptFactory); this.schemaLoader=Objects.requireNonNull(schemaLoader);
        this.validator=Objects.requireNonNull(validator); this.mapper=Objects.requireNonNull(mapper); this.config=Objects.requireNonNull(config);
    }
    @Override public AnswerEvaluation evaluate(AnswerEvaluationRequest request) {
        StructuredCodexRequest structured;
        try { structured = new StructuredCodexRequest("answer-evaluation", promptFactory.create(request), schemaLoader.load(),
                    config.defaultModel(), ReasoningEffort.LOW, Duration.ofSeconds(120)); }
        catch (IllegalStateException exception) { throw new CodexExecutionException(-1, "Answer evaluation resources are unavailable."); }
        StructuredCodexResult result = provider.execute(structured);
        try {
            AnswerEvaluationValidator.Payload payload = mapper.readerFor(AnswerEvaluationValidator.Payload.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(result.finalJson());
            return validator.validate(payload, request);
        } catch (JsonProcessingException exception) { throw new InvalidCodexResponseException(INVALID); }
    }
}
