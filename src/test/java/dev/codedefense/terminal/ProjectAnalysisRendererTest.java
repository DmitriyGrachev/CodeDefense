package dev.codedefense.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.TechnicalQuestion;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectAnalysisRendererTest {
    @Test
    void rendersOnlyTheApprovedProjectOverview() {
        String rendered = render(analysis());

        assertTrue(rendered.contains("Project: Fixture project"));
        assertTrue(rendered.contains("Type: Java CLI"));
        assertTrue(rendered.contains("Summary\nA focused fixture application."));
        assertTrue(rendered.contains("Main flow\n1. The command parses its arguments."));
        assertTrue(rendered.contains("2. The application validates the request."));
        assertTrue(rendered.contains("Key components\n- Command [entry-point]"));
        assertTrue(rendered.contains("  Parses command-line arguments."));
        assertTrue(rendered.contains("  Paths: src/App.java, src/Service.java"));
        assertTrue(rendered.contains("Critical topics\n- startup\n- validation"));
        assertTrue(rendered.contains("Prepared technical questions: 3"));
        assertTrue(rendered.contains("Project analysis completed."));
        assertTrue(rendered.contains("The adaptive defense will be connected in Iteration 6."));
    }

    @Test
    void hidesAllQuestionInternalsAndRemovesTerminalControlSequences() {
        String rendered = render(analysis());

        assertFalse(rendered.contains("DO_NOT_RENDER_QUESTION_PROMPT"));
        assertFalse(rendered.contains("DO_NOT_RENDER_EXPECTED_KEY_POINT"));
        assertFalse(rendered.contains("DO_NOT_RENDER_EVIDENCE_REASON"));
        assertFalse(rendered.contains("RAW_MODEL_JSON"));
        assertFalse(rendered.contains("\u001B"));
        assertFalse(rendered.contains("\u0000"));
        assertFalse(rendered.contains("CHANGE_TERMINAL_TITLE"));
        assertTrue(rendered.contains("Readable Unicode: Привет 👋"));
    }

    private static String render(ProjectAnalysis analysis) {
        StringWriter characters = new StringWriter();
        new ProjectAnalysisRenderer().render(analysis, new PrintWriter(characters));
        return characters.toString().replace("\r\n", "\n");
    }

    private static ProjectAnalysis analysis() {
        return new ProjectAnalysis(
                "Fixture project",
                "Java CLI",
                "A focused fixture application.\u001B[2J\u001B]0;CHANGE_TERMINAL_TITLE\u0007\u0000 Readable Unicode: Привет 👋",
                List.of("The command parses its arguments.\r\n", "The application validates the request."),
                List.of(new ProjectComponent(
                        "Command", "entry-point", "Parses command-line arguments.",
                        List.of("src/App.java", "src/Service.java"))),
                List.of("startup", "validation"),
                List.of(question("startup"), question("flow"), question("validation")));
    }

    private static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(
                id,
                "DO_NOT_RENDER_QUESTION_PROMPT " + id,
                "Understand " + id,
                List.of("DO_NOT_RENDER_EXPECTED_KEY_POINT", "Second private point"),
                List.of(new CodeEvidence("src/App.java", 1, 1,
                        "DO_NOT_RENDER_EVIDENCE_REASON RAW_MODEL_JSON")));
    }
}
