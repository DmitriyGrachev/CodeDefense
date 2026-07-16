package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAnalysisTest {
    @Test
    void copiesCollectionsAndStripsTextFields() {
        var componentPaths = new ArrayList<>(List.of(" src\\Application.java "));
        var component = new ProjectComponent(" Application ", " entry-point ", " Starts the application ", componentPaths);
        var keyPoints = new ArrayList<>(List.of(" Explains startup ", "Explains dependencies"));
        var evidence = new ArrayList<>(List.of(new CodeEvidence(" src\\Application.java ", 1, 3, " Entry point ")));
        var question = new TechnicalQuestion(" startup ", " How does startup work? ", " Understand startup ", keyPoints, evidence);
        var mainFlow = new ArrayList<>(List.of(" Parse arguments ", "Start application"));
        var components = new ArrayList<>(List.of(component));
        var topics = new ArrayList<>(List.of(" startup ", "validation"));
        var questions = new ArrayList<>(List.of(question, question("flow"), question("safety")));

        ProjectAnalysis analysis = new ProjectAnalysis(
                " CodeDefense ", " Java CLI ", " A command-line application ",
                mainFlow, components, topics, questions);

        componentPaths.add("src/Unexpected.java");
        keyPoints.add("Unexpected key point");
        evidence.add(new CodeEvidence("src/Unexpected.java", 1, 1, "Unexpected evidence"));
        mainFlow.add("Unexpected flow");
        components.clear();
        topics.clear();
        questions.clear();

        assertEquals("CodeDefense", analysis.projectName());
        assertEquals("Application", component.name());
        assertEquals(List.of("src\\Application.java"), component.paths());
        assertEquals("src\\Application.java", question.evidence().getFirst().path());
        assertEquals(List.of("Explains startup", "Explains dependencies"), question.expectedKeyPoints());
        assertEquals(List.of("Parse arguments", "Start application"), analysis.mainFlow());
        assertEquals(List.of("startup", "validation"), analysis.criticalTopics());
        assertThrows(UnsupportedOperationException.class, () -> component.paths().add("src/Other.java"));
        assertThrows(UnsupportedOperationException.class, () -> question.evidence().clear());
        assertThrows(UnsupportedOperationException.class, () -> analysis.questions().clear());
    }

    @Test
    void enforcesProjectAnalysisCollectionBounds() {
        ProjectAnalysis valid = analysis();

        assertThrows(IllegalArgumentException.class, () -> copy(valid, List.of("only one"), valid.components(), valid.criticalTopics(), valid.questions()));
        assertThrows(IllegalArgumentException.class, () -> copy(valid, repeated(9, "flow"), valid.components(), valid.criticalTopics(), valid.questions()));
        assertThrows(IllegalArgumentException.class, () -> copy(valid, valid.mainFlow(), List.of(), valid.criticalTopics(), valid.questions()));
        assertThrows(IllegalArgumentException.class, () -> copy(valid, valid.mainFlow(), repeatedComponents(13), valid.criticalTopics(), valid.questions()));
        assertThrows(IllegalArgumentException.class, () -> copy(valid, valid.mainFlow(), valid.components(), List.of("only one"), valid.questions()));
        assertThrows(IllegalArgumentException.class, () -> copy(valid, valid.mainFlow(), valid.components(), repeated(9, "topic"), valid.questions()));
        assertThrows(IllegalArgumentException.class, () -> copy(valid, valid.mainFlow(), valid.components(), valid.criticalTopics(), valid.questions().subList(0, 2)));
    }

    @Test
    void enforcesComponentQuestionAndEvidenceBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectComponent("Name", "kind", "Responsibility", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProjectComponent("Name", "kind", "Responsibility", repeated(6, "src/App.java")));
        assertThrows(IllegalArgumentException.class, () -> new TechnicalQuestion("id", "A repository question?", "Goal", List.of("one"), List.of(evidence())));
        assertThrows(IllegalArgumentException.class, () -> new TechnicalQuestion("id", "A repository question?", "Goal", repeated(7, "point"), List.of(evidence())));
        assertThrows(IllegalArgumentException.class, () -> new TechnicalQuestion("id", "A repository question?", "Goal", List.of("one", "two"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TechnicalQuestion("id", "A repository question?", "Goal", List.of("one", "two"), repeatedEvidence(4)));
    }

    @Test
    void performsOnlyGenericEvidencePathValidation() {
        CodeEvidence evidence = new CodeEvidence(" ../outside.java ", 1, 2, " Reason ");

        assertEquals("../outside.java", evidence.path());
        assertEquals("Reason", evidence.reason());
        assertThrows(IllegalArgumentException.class, () -> new CodeEvidence("src/App.java", 0, 1, "Reason"));
        assertThrows(IllegalArgumentException.class, () -> new CodeEvidence("src/App.java", 2, 1, "Reason"));
        assertThrows(IllegalArgumentException.class, () -> new CodeEvidence(" ", 1, 1, "Reason"));
    }

    @Test
    void rejectsBlankAndNullFields() {
        assertThrows(IllegalArgumentException.class, () -> new CodeEvidence("src/App.java", 1, 1, " "));
        assertThrows(IllegalArgumentException.class, () -> new ProjectComponent(" ", "adapter", "Starts", List.of("src/App.java")));
        assertThrows(IllegalArgumentException.class, () -> new TechnicalQuestion("id", " ", "Goal", List.of("One", "Two"), List.of(evidence())));
        assertThrows(IllegalArgumentException.class, () -> new ProjectAnalysis(
                " ", "Java", "Summary", List.of("one", "two"), List.of(component()), List.of("one", "two"),
                List.of(question("one"), question("two"), question("three"))));
        assertThrows(NullPointerException.class, () -> new CodeEvidence(null, 1, 1, "Reason"));
    }

    @Test
    void projectAnalysisToStringHidesQuestionInternals() {
        ProjectAnalysis analysis = analysis();
        String rendered = analysis.toString();

        assertTrue(rendered.contains("CodeDefense"));
        assertFalse(rendered.contains("private-key-point"));
        assertFalse(rendered.contains("How does startup work?"));
        assertFalse(rendered.contains("evidence-reason"));
    }

    private static ProjectAnalysis analysis() {
        return new ProjectAnalysis(
                "CodeDefense", "Java CLI", "A command-line application",
                List.of("Parse arguments", "Start application"),
                List.of(component()),
                List.of("startup", "validation"),
                List.of(question("startup"), question("flow"), question("safety")));
    }

    private static ProjectAnalysis copy(ProjectAnalysis base, List<String> mainFlow,
            List<ProjectComponent> components, List<String> topics, List<TechnicalQuestion> questions) {
        return new ProjectAnalysis(base.projectName(), base.projectType(), base.summary(), mainFlow, components, topics, questions);
    }

    private static ProjectComponent component() {
        return new ProjectComponent("Application", "entry-point", "Starts the application", List.of("src/App.java"));
    }

    private static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(id, "How does " + id + " work?", "Understand " + id,
                List.of("private-key-point", "second point"),
                List.of(new CodeEvidence("src/App.java", 1, 1, "evidence-reason")));
    }

    private static CodeEvidence evidence() {
        return new CodeEvidence("src/App.java", 1, 1, "Reason");
    }

    private static List<String> repeated(int count, String value) {
        return java.util.Collections.nCopies(count, value);
    }

    private static List<ProjectComponent> repeatedComponents(int count) {
        var result = new ArrayList<ProjectComponent>();
        for (int index = 0; index < count; index++) {
            result.add(new ProjectComponent("Component " + index, "kind", "Responsibility", List.of("src/App.java")));
        }
        return result;
    }

    private static List<CodeEvidence> repeatedEvidence(int count) {
        var result = new ArrayList<CodeEvidence>();
        for (int index = 1; index <= count; index++) {
            result.add(new CodeEvidence("src/App.java", index, index, "Reason"));
        }
        return result;
    }
}
