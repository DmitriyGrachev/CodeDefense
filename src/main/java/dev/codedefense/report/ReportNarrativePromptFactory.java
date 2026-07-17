package dev.codedefense.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.domain.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public final class ReportNarrativePromptFactory {
    private static final String RESOURCE = "prompts/generate-report.md";
    private static final int MAX_BYTES = 64 * 1024;
    private final ClassLoader classLoader;
    private final ObjectMapper mapper = new ObjectMapper();
    public ReportNarrativePromptFactory() { this(ReportNarrativePromptFactory.class.getClassLoader()); }
    ReportNarrativePromptFactory(ClassLoader classLoader) { this.classLoader = Objects.requireNonNull(classLoader); }

    public String create(ReportGenerationRequest request, ReportMetadata metadata) {
        Objects.requireNonNull(request); Objects.requireNonNull(metadata);
        String payload = payload(request, metadata);
        String marker = markerFor(payload);
        return instructions() + "\n\nBEGIN " + marker + "\n" + payload + "\nEND " + marker + "\n";
    }
    private String payload(ReportGenerationRequest request, ReportMetadata metadata) {
        ProjectAnalysis analysis = request.analysis(); InterviewSession session = request.session();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("project", Map.of("name", metadata.projectName(), "type", metadata.projectType(), "model", metadata.model(), "analyzedAt", metadata.analyzedAt().toString(), "selectedFiles", metadata.selectedFiles(), "snapshotBytes", metadata.snapshotBytes(), "redactionCount", metadata.redactionCount()));
        root.put("overview", Map.of("summary", analysis.summary(), "mainFlow", analysis.mainFlow(), "criticalTopics", analysis.criticalTopics()));
        List<Map<String, Object>> questions = new ArrayList<>();
        for (QuestionResult result : session.results()) {
            TechnicalQuestion question = result.question(); AnswerEvaluation evaluation = result.primaryTurn().evaluation();
            Map<String, Object> evaluationPayload = new LinkedHashMap<>();
            evaluationPayload.put("verdict", evaluation.verdict().name());
            evaluationPayload.put("feedback", evaluation.feedback());
            evaluationPayload.put("understoodConcepts", evaluation.understoodConcepts());
            evaluationPayload.put("missingConcepts", evaluation.missingConcepts());
            evaluationPayload.put("followUpQuestion", evaluation.followUpQuestion().orElse(""));
            questions.add(Map.of("id", question.id(), "prompt", question.prompt(),
                    "evaluation", evaluationPayload, "finalScore", result.finalScore()));
        }
        root.put("questions", questions);
        root.put("localResults", Map.of("overallScore", session.overallScore(), "readiness", session.readiness().name(), "skippedQuestionCount", session.skippedQuestionCount()));
        try { return mapper.writeValueAsString(root); } catch (JsonProcessingException e) { throw unavailable(); }
    }
    private String instructions() {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) { if (input == null) throw unavailable(); return normalize(decode(read(input))); }
        catch (IOException | RuntimeException e) { throw unavailable(); }
    }
    private static byte[] read(InputStream input) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; for (int n; (n=input.read(b))!=-1;) { if(out.size()+n>MAX_BYTES) throw unavailable(); out.write(b,0,n); } return out.toByteArray(); }
    private static String decode(byte[] bytes) throws CharacterCodingException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
    private static String normalize(String value) { return value.replace("\r\n", "\n").replace('\r', '\n'); }
    private static String markerFor(String payload) { String marker="CODEDEFENSE_UNTRUSTED_REPORT"; while(payload.contains(marker)) marker += "_X"; return marker; }
    private static IllegalStateException unavailable() { return new IllegalStateException("Report narrative prompt resource is unavailable."); }
}
