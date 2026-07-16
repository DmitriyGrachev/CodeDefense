package dev.codedefense.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.TechnicalQuestion;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAnalysisValidatorTest {
    private static final String SAFE_MESSAGE = "Codex returned an invalid project analysis.";
    private final ProjectAnalysisValidator validator = new ProjectAnalysisValidator();

    @Test
    void acceptsAndNormalizesValidAnalysisUsingCanonicalSnapshotMetadata() {
        ProjectAnalysis modelAnalysis = validAnalysis();
        ProjectAnalysis validated = validator.validate(modelAnalysis, snapshot());

        assertEquals("local-project", validated.projectName());
        assertEquals("Java CLI", validated.projectType());
        assertEquals(List.of("src/App.java", "src/Service.java"), validated.components().getFirst().paths());
        assertEquals("src/App.java", validated.questions().getFirst().evidence().getFirst().path());
    }

    @Test
    void rejectsDuplicateAndUnsafeQuestionIds() {
        assertInvalid(withQuestions(List.of(
                question("same", "How does command startup work?", evidence("src/App.java", 1, 2)),
                question("same", "How does service dispatch work?", evidence("src/Service.java", 1, 2)),
                question("risk", "Which validation failure matters?", evidence("config/app.yml", 1, 2)))));
        assertInvalid(replaceQuestion(0, question("not valid", "How does command startup work?", evidence("src/App.java", 1, 2))));
    }

    @Test
    void rejectsPromptsAndExpectedKeyPointsThatDuplicateAfterNormalization() {
        assertInvalid(withQuestions(List.of(
                question("one", "How   does command STARTUP work?", evidence("src/App.java", 1, 2)),
                question("two", " how does command startup WORK? ", evidence("src/Service.java", 1, 2)),
                question("three", "Which validation failure matters?", evidence("config/app.yml", 1, 2)))));

        TechnicalQuestion duplicatePoints = new TechnicalQuestion(
                "startup", "How does command startup work?", "Understand startup",
                List.of(" Trace startup flow ", "trace   STARTUP flow"),
                List.of(evidence("src/App.java", 1, 2)));
        assertInvalid(replaceQuestion(0, duplicatePoints));
    }

    @Test
    void rejectsMissingOrDuplicateEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new TechnicalQuestion(
                "id", "How does this component work?", "Understand it",
                List.of("First point", "Second point"), List.of()));

        TechnicalQuestion duplicateEvidence = new TechnicalQuestion(
                "startup", "How does command startup work?", "Understand startup",
                List.of("First point", "Second point"),
                List.of(evidence("src/App.java", 1, 2), new CodeEvidence("src\\App.java", 1, 2, "Another reason")));
        assertInvalid(replaceQuestion(0, duplicateEvidence));
    }

    @Test
    void rejectsUnknownEvidenceAndComponentPaths() {
        assertInvalid(replaceQuestion(0, question(
                "startup", "How does command startup work?", evidence("src/Missing.java", 1, 2))));

        ProjectAnalysis base = validAnalysis();
        assertInvalid(copy(base, List.of(new ProjectComponent(
                "Application", "entry-point", "Starts the command", List.of("src/Missing.java"))),
                base.criticalTopics(), base.questions()));
    }

    @Test
    void rejectsAbsoluteDriveTraversalDotAndEmptyPathSegments() {
        for (String path : List.of(
                "/src/App.java", "C:/src/App.java", "src/../App.java", "src/./App.java", "src//App.java")) {
            assertInvalid(replaceQuestion(0, question(
                    "startup", "How does command startup work?", evidence(path, 1, 2))));
        }
    }

    @Test
    void rejectsInvalidAndOutOfSnapshotLineRanges() {
        assertThrows(IllegalArgumentException.class, () -> evidence("src/App.java", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> evidence("src/App.java", 2, 1));
        assertInvalid(replaceQuestion(0, question(
                "startup", "How does command startup work?", evidence("src/App.java", 1, 21))));
    }

    @Test
    void rejectsDuplicateComponentsAndComponentPathsAfterNormalization() {
        ProjectAnalysis base = validAnalysis();
        assertInvalid(copy(base, List.of(new ProjectComponent(
                "Application", "entry-point", "Starts the command",
                List.of("src/App.java", "src\\App.java"))), base.criticalTopics(), base.questions()));

        List<ProjectComponent> duplicates = List.of(
                new ProjectComponent(" Application ", "entry-point", "Starts", List.of("src/App.java", "src/Service.java")),
                new ProjectComponent("application", "domain", "Coordinates", List.of("src\\Service.java", "src\\App.java")));
        assertInvalid(copy(base, duplicates, base.criticalTopics(), base.questions()));
    }

    @Test
    void rejectsCriticalTopicsThatDuplicateAfterNormalization() {
        ProjectAnalysis base = validAnalysis();
        assertInvalid(copy(base, base.components(),
                List.of(" Input   validation ", "input VALIDATION"), base.questions()));
    }

    @Test
    void enforcesExactSchemaBoundsLocally() {
        ProjectAnalysis base = validAnalysis();
        assertInvalid(new ProjectAnalysis(base.projectName(), base.projectType(), "x".repeat(19),
                base.mainFlow(), base.components(), base.criticalTopics(), base.questions()));
        assertInvalid(copy(base, List.of(new ProjectComponent(
                "Name", "kind", "tiny", List.of("src/App.java"))), base.criticalTopics(), base.questions()));
        TechnicalQuestion shortPrompt = new TechnicalQuestion(
                "id", "too short", "Goal", List.of("One point", "Two points"), List.of(evidence("src/App.java", 1, 1)));
        assertInvalid(replaceQuestion(0, shortPrompt));
    }

    @Test
    void failuresUseOneSafeMessage() {
        String secret = "raw-model-secret";
        InvalidCodexResponseException exception = assertInvalid(replaceQuestion(0, question(
                "startup", "How does command startup work?", evidence("src/" + secret + ".java", 1, 2))));

        assertEquals(SAFE_MESSAGE, exception.getMessage());
        assertFalse(exception.getMessage().contains(secret));
        assertFalse(exception.getMessage().contains("private snapshot content"));
    }

    private InvalidCodexResponseException assertInvalid(ProjectAnalysis analysis) {
        InvalidCodexResponseException exception = assertThrows(
                InvalidCodexResponseException.class, () -> validator.validate(analysis, snapshot()));
        assertEquals(SAFE_MESSAGE, exception.getMessage());
        return exception;
    }

    private static ProjectAnalysis validAnalysis() {
        return new ProjectAnalysis(
                "model-project", "Hallucinated type", "A small fixture application for project analysis.",
                List.of("The CLI parses input", "The service validates input"),
                List.of(new ProjectComponent(
                        "Application", "entry-point", "Starts the command", List.of("src\\App.java", "src\\Service.java"))),
                List.of("startup", "validation"),
                List.of(
                        question("startup", "How does command startup work?", evidence("src\\App.java", 1, 2)),
                        question("service", "How does service dispatch work?", evidence("src/Service.java", 1, 2)),
                        question("risk", "Which validation failure matters?", evidence("config/app.yml", 1, 2))));
    }

    private static ProjectAnalysis withQuestions(List<TechnicalQuestion> questions) {
        ProjectAnalysis base = validAnalysis();
        return new ProjectAnalysis(base.projectName(), base.projectType(), base.summary(), base.mainFlow(),
                base.components(), base.criticalTopics(), questions);
    }

    private static ProjectAnalysis replaceQuestion(int index, TechnicalQuestion replacement) {
        var questions = new java.util.ArrayList<>(validAnalysis().questions());
        questions.set(index, replacement);
        return withQuestions(questions);
    }

    private static ProjectAnalysis copy(ProjectAnalysis base, List<ProjectComponent> components,
            List<String> criticalTopics, List<TechnicalQuestion> questions) {
        return new ProjectAnalysis(base.projectName(), base.projectType(), base.summary(), base.mainFlow(),
                components, criticalTopics, questions);
    }

    private static TechnicalQuestion question(String id, String prompt, CodeEvidence evidence) {
        return new TechnicalQuestion(id, prompt, "Understand repository behavior",
                List.of("Trace the code path", "Explain the design choice"), List.of(evidence));
    }

    private static CodeEvidence evidence(String path, int start, int end) {
        return new CodeEvidence(path, start, end, "Supports the question");
    }

    private static ProjectSnapshot snapshot() {
        String prompt = "private snapshot content";
        List<ProjectSnapshot.SelectedFile> selected = List.of(
                new ProjectSnapshot.SelectedFile(Path.of("src/App.java"), 20, false, 100),
                new ProjectSnapshot.SelectedFile(Path.of("src/Service.java"), 12, false, 90),
                new ProjectSnapshot.SelectedFile(Path.of("config/app.yml"), 5, false, 40));
        List<SourceFile> candidates = selected.stream().map(file -> new SourceFile(file.relativePath(), 10)).toList();
        return new ProjectSnapshot(
                Path.of(".").toAbsolutePath().normalize(), "local-project", "Java CLI",
                new ScanSummary(Path.of(".").toAbsolutePath().normalize(), 3, 0, candidates),
                selected, prompt, prompt.getBytes(StandardCharsets.UTF_8).length, 0);
    }
}
