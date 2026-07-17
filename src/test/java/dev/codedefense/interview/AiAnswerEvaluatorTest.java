package dev.codedefense.interview;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.*;
import dev.codedefense.ai.exception.*;
import dev.codedefense.domain.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiAnswerEvaluatorTest {
    @Test void makesExactlyOneLowReasoningRequestAndParsesStrictJson() {
        AtomicInteger calls = new AtomicInteger();
        AiProvider provider = request -> {
            calls.incrementAndGet();
            assertEquals("answer-evaluation", request.operationName());
            assertEquals(ReasoningEffort.LOW, request.reasoningEffort());
            assertEquals(Duration.ofSeconds(120), request.timeout());
            return new StructuredCodexResult("{\"verdict\":\"CORRECT\",\"score\":90,\"feedback\":\"Clear repository-specific answer.\",\"understoodConcepts\":[],\"missingConcepts\":[],\"followUpQuestion\":\"\"}", Duration.ZERO, request.model());
        };
        AnswerEvaluation result = new AiAnswerEvaluator(provider).evaluate(AnswerEvaluationPromptFactoryTest.request("project", "answer"));
        assertEquals(1, calls.get());
        assertEquals(Verdict.CORRECT, result.verdict());
    }
    @Test void rejectsUnknownOrTrailingJsonAndDoesNotLeakRawJson() {
        for (String json : new String[]{"{} {}", "{\"unknown\":true}"}) {
            AiAnswerEvaluator evaluator = new AiAnswerEvaluator(request -> new StructuredCodexResult(json, Duration.ZERO, "model"));
            InvalidCodexResponseException error = assertThrows(InvalidCodexResponseException.class,
                    () -> evaluator.evaluate(AnswerEvaluationPromptFactoryTest.request("project", "answer")));
            assertEquals("Codex returned an invalid answer evaluation.", error.getMessage());
            assertFalse(error.toString().contains(json));
        }
    }
    @Test void mapsResourceFailuresBeforeProviderInvocationAndPropagatesProviderErrors() {
        AtomicInteger calls = new AtomicInteger();
        AiProvider provider = request -> { calls.incrementAndGet(); throw new CodexTimeoutException(); };
        AiAnswerEvaluator unavailable = new AiAnswerEvaluator(provider,
                new AnswerEvaluationPromptFactory(AnswerEvaluationPromptFactoryTest.loader(null)), new AnswerEvaluationSchemaLoader(),
                new AnswerEvaluationValidator(), new ObjectMapper(), CodexRuntimeConfig.defaults());
        CodexExecutionException error = assertThrows(CodexExecutionException.class,
                () -> unavailable.evaluate(AnswerEvaluationPromptFactoryTest.request("project", "answer")));
        assertTrue(error.getMessage().contains("Answer evaluation resources are unavailable."));
        assertEquals(0, calls.get());
        assertThrows(CodexTimeoutException.class, () -> new AiAnswerEvaluator(provider).evaluate(AnswerEvaluationPromptFactoryTest.request("project", "answer")));
    }
}
