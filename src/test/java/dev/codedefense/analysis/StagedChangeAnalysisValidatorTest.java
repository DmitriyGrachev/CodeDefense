package dev.codedefense.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class StagedChangeAnalysisValidatorTest {
    private static final String SAFE_ERROR = "Codex returned an invalid staged change analysis.";

    @Test
    void acceptsExactlyTheThreeTypedQuestionIds() {
        ProjectAnalysis validated = new StagedChangeAnalysisValidator().validate(analysis(
                "decision", "counterfactual", "test-prediction"), snapshot());

        assertEquals(List.of("decision", "counterfactual", "test-prediction"),
                validated.questions().stream().map(TechnicalQuestion::id).toList());
    }

    @Test
    void rejectsWrongOrDuplicateTypedQuestionIdsWithOneSafeMessage() {
        for (ProjectAnalysis invalid : List.of(
                analysis("decision", "counterfactual", "other"),
                analysis("decision", "decision", "test-prediction"))) {
            InvalidCodexResponseException exception = assertThrows(InvalidCodexResponseException.class,
                    () -> new StagedChangeAnalysisValidator().validate(invalid, snapshot()));
            assertEquals(SAFE_ERROR, exception.getMessage());
        }
    }

    @Test
    void rejectsEvidenceOutsideSelectedStagedLines() {
        ProjectAnalysis invalid = analysisWithEvidence("src/Missing.java", 1, 1);

        InvalidCodexResponseException exception = assertThrows(InvalidCodexResponseException.class,
                () -> new StagedChangeAnalysisValidator().validate(invalid, snapshot()));

        assertEquals(SAFE_ERROR, exception.getMessage());
    }

    static ProjectAnalysis analysis(String first, String second, String third) {
        return new ProjectAnalysis("model-name", "model-type", "A bounded staged change analysis for a fixture.",
                List.of("The command invokes an application service.", "The service validates supplied input."),
                List.of(new ProjectComponent("Application", "entry-point", "Starts the staged fixture.",
                        List.of("src/App.java"))),
                List.of("startup", "validation"),
                List.of(question(first, "src/App.java", 1, 2), question(second, "src/App.java", 3, 4),
                        question(third, "src/Service.java", 1, 2)));
    }

    static ProjectAnalysis analysisWithEvidence(String path, int start, int end) {
        ProjectAnalysis base = analysis("decision", "counterfactual", "test-prediction");
        return new ProjectAnalysis(base.projectName(), base.projectType(), base.summary(), base.mainFlow(),
                base.components(), base.criticalTopics(), List.of(
                        base.questions().get(0), base.questions().get(1), question("test-prediction", path, start, end)));
    }

    static ProjectSnapshot snapshot() {
        String content = "INDEX_FILE: src/App.java\n1 | class App {}\n";
        Path root = Path.of(".").toAbsolutePath().normalize();
        return new ProjectSnapshot(root, "fixture-project", "Staged Git change",
                new ScanSummary(root, 2, 0, List.of(new SourceFile(Path.of("src/App.java")),
                        new SourceFile(Path.of("src/Service.java")))),
                List.of(new ProjectSnapshot.SelectedFile(Path.of("src/App.java"), 4, false, 70),
                        new ProjectSnapshot.SelectedFile(Path.of("src/Service.java"), 2, false, 50)),
                content, content.getBytes(StandardCharsets.UTF_8).length, 0);
    }

    private static TechnicalQuestion question(String id, String path, int start, int end) {
        return new TechnicalQuestion(id, "How does the staged " + id + " behavior work?",
                "Explain the staged change behavior.", List.of("Trace the changed path", "Explain the consequence"),
                List.of(new CodeEvidence(path, start, end, "Supports the staged question.")));
    }
}
