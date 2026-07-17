package dev.codedefense.interview;

import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.AnswerEvaluationRequest;
import dev.codedefense.domain.CodeEvidence;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Objects;

public final class AnswerEvaluationPromptFactory {
    private static final String RESOURCE = "prompts/evaluate-answer.md";
    private static final int MAX_BYTES = 64 * 1024;
    private static final String MESSAGE = "Answer evaluation prompt resource is unavailable.";
    private final ClassLoader classLoader;

    public AnswerEvaluationPromptFactory() { this(AnswerEvaluationPromptFactory.class.getClassLoader()); }
    AnswerEvaluationPromptFactory(ClassLoader classLoader) { this.classLoader = Objects.requireNonNull(classLoader); }

    public String create(AnswerEvaluationRequest request) {
        Objects.requireNonNull(request, "request");
        StringBuilder payload = new StringBuilder()
                .append("Project name: ").append(request.projectName()).append('\n')
                .append("Project type: ").append(request.projectType()).append('\n')
                .append("Project summary: ").append(request.projectSummary()).append('\n')
                .append("Evaluation stage: ").append(request.stage()).append('\n')
                .append("Question: ").append(request.primaryQuestion().prompt()).append('\n')
                .append("Learning goal: ").append(request.primaryQuestion().learningGoal()).append('\n')
                .append("Expected key points:\n");
        request.primaryQuestion().expectedKeyPoints().forEach(value -> payload.append("- ").append(value).append('\n'));
        payload.append("Evidence:\n");
        for (CodeEvidence evidence : request.primaryQuestion().evidence()) {
            payload.append("- ").append(evidence.path()).append(':').append(evidence.startLine()).append('-')
                    .append(evidence.endLine()).append(" — ").append(evidence.reason()).append('\n');
        }
        payload.append("Primary answer: ").append(request.primaryAnswer()).append('\n')
                .append("Current prompt: ").append(request.currentPrompt()).append('\n')
                .append("Current answer: ").append(request.currentAnswer()).append('\n');
        request.previousEvaluation().ifPresent(previous -> appendPrevious(payload, previous));
        String content = payload.toString();
        String boundary = "CODEDEFENSE_UNTRUSTED_EVALUATION";
        while (content.contains(boundary)) boundary += "_X";
        return load() + "\n\nBEGIN " + boundary + "\n" + content + "END " + boundary + "\n";
    }

    private static void appendPrevious(StringBuilder target, AnswerEvaluation value) {
        target.append("Previous verdict: ").append(value.verdict()).append('\n')
                .append("Previous score: ").append(value.score()).append('\n')
                .append("Previous feedback: ").append(value.feedback()).append('\n')
                .append("Previously understood: ").append(String.join(" | ", value.understoodConcepts())).append('\n')
                .append("Previously missing: ").append(String.join(" | ", value.missingConcepts())).append('\n')
                .append("Previous follow-up: ").append(value.followUpQuestion().orElse("")).append('\n');
    }

    private String load() {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) throw unavailable();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAX_BYTES) throw unavailable();
                output.write(buffer, 0, read);
            }
            String decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString();
            return decoded.replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException | RuntimeException exception) { throw unavailable(); }
    }
    private static IllegalStateException unavailable() { return new IllegalStateException(MESSAGE); }
}
