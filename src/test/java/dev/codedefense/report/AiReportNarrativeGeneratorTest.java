package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.*;
import dev.codedefense.ai.exception.*;
import dev.codedefense.domain.*;
import java.time.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class AiReportNarrativeGeneratorTest {
    @Test void sendsExactlyOneLowReasoningConfiguredNarrativeRequest() throws Exception {
        AtomicReference<StructuredCodexRequest> seen = new AtomicReference<>(); AtomicInteger calls = new AtomicInteger();
        String response = new ObjectMapper().writeValueAsString(Fixtures.narrative());
        AiProvider provider = r -> { calls.incrementAndGet(); seen.set(r); return new StructuredCodexResult(response, Duration.ZERO, "model"); };
        ReportNarrative actual = new AiReportNarrativeGenerator(provider).generate(Fixtures.request(), Fixtures.metadata());
        assertEquals(Fixtures.narrative(), actual); assertEquals(1, calls.get());
        assertEquals("report-narrative", seen.get().operationName()); assertEquals(ReasoningEffort.LOW, seen.get().reasoningEffort()); assertEquals(Duration.ofSeconds(120), seen.get().timeout()); assertEquals("gpt-5.6-terra", seen.get().model());
    }
    @Test void mapsResourceFailureBeforeProviderCall() {
        AtomicInteger calls = new AtomicInteger(); AiProvider provider = r -> { calls.incrementAndGet(); throw new AssertionError(); };
        AiReportNarrativeGenerator generator = new AiReportNarrativeGenerator(provider, new ReportNarrativePromptFactory(new ClassLoader(null) {}), new ReportNarrativeSchemaLoader(), new ReportNarrativeValidator(), new ObjectMapper(), CodexRuntimeConfig.defaults(), ReportConfig.defaults());
        CodexExecutionException error = assertThrows(CodexExecutionException.class, () -> generator.generate(Fixtures.request(), Fixtures.metadata()));
        assertEquals("Codex execution failed with exit code -1.\nReport narrative resources are unavailable.", error.getMessage()); assertEquals(0, calls.get());
    }
    @Test void rejectsUnknownAndTrailingJsonWithoutLeakingRawResponse() {
        for (String raw : new String[] {"{\"headline\":\"Useful report headline\",\"summary\":\"A sufficiently detailed summary that does not claim a score.\",\"strengths\":[],\"knowledgeGaps\":[],\"recommendedActions\":[\"Practice the main flow\"],\"private\":\"raw-secret\"}", newJson() + " {\"private\":\"raw-secret\"}"}) {
            AtomicInteger calls = new AtomicInteger(); AiProvider provider = r -> { calls.incrementAndGet(); return new StructuredCodexResult(raw, Duration.ZERO, "model"); };
            InvalidCodexResponseException error = assertThrows(InvalidCodexResponseException.class, () -> new AiReportNarrativeGenerator(provider).generate(Fixtures.request(), Fixtures.metadata()));
            assertEquals("Codex returned an invalid report narrative.", error.getMessage()); assertFalse(error.getMessage().contains("raw-secret")); assertEquals(1, calls.get());
        }
    }
    private static String newJson() { return "{\"headline\":\"Useful report headline\",\"summary\":\"A sufficiently detailed summary that does not claim a score.\",\"strengths\":[],\"knowledgeGaps\":[],\"recommendedActions\":[\"Practice the main flow\"]}"; }
}
