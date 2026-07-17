package dev.codedefense.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownReportRendererTest {
    @Test
    void renders_a_deterministic_private_safe_local_report_with_followup_and_skipped_question() {
        FinalReport report = report();
        MarkdownReportRenderer renderer = new MarkdownReportRenderer();

        String markdown = renderer.render(report);

        assertEquals(markdown, renderer.render(report));
        assertTrue(markdown.startsWith("# CodeDefense Understanding Report\n"));
        assertTrue(markdown.contains("Overall score: 73/100"));
        assertTrue(markdown.contains("Readiness: Review needed"));
        assertTrue(markdown.contains("## Question 1\n"));
        assertTrue(markdown.contains("## Question 2\n"));
        assertTrue(markdown.contains("## Question 3\n"));
        assertTrue(markdown.contains("Skipped by user."));
        assertTrue(markdown.contains("### Follow-up\n"));
        assertTrue(markdown.contains("````text\nanswer with ``` fence\n````"));
        assertTrue(markdown.contains("Generated with the local deterministic fallback"));
        assertTrue(markdown.contains("## Privacy\n"));
        assertTrue(markdown.contains("validated feedback and concepts"));
        assertFalse(markdown.contains("PRIVATE_EXPECTED_KEY"));
        assertFalse(markdown.contains("PRIVATE_EVIDENCE_REASON"));
        assertFalse(markdown.contains("PRIVATE_SNAPSHOT"));
        assertFalse(markdown.contains("PRIVATE_RAW_JSON"));
        assertFalse(markdown.contains("\r"));
        assertTrue(markdown.endsWith("\n"));
    }

    @Test
    void escapes_every_rendered_field_and_preserves_analyzed_file_order_without_rendering_private_inputs() {
        NonRenderedInputs fixture = hostileReport();
        String markdown = new MarkdownReportRenderer().render(fixture.report());

        assertTrue(markdown.contains("model\\# \\[link\\]\\(target\\) \\| table \\| \\\\"));
        assertTrue(markdown.contains("feedback\\# \\[link\\]\\(target\\) \\| table \\| \\\\"));
        assertTrue(markdown.contains("answer # [link](target) | table | \\"));
        assertTrue(markdown.indexOf("zeta\\.md") < markdown.indexOf("alpha\\.md"));
        assertFalse(markdown.contains("PRIVATE_EXPECTED_KEY"));
        assertFalse(markdown.contains("PRIVATE_EVIDENCE_REASON"));
        assertTrue(fixture.snapshot().promptContent().contains("PRIVATE_SNAPSHOT"));
        assertTrue(fixture.rawModelJson().contains("PRIVATE_RAW_JSON"));
        assertFalse(fixture.report().narrative().headline().contains("PRIVATE_RAW_JSON"));
        assertFalse(fixture.report().narrative().summary().contains("PRIVATE_RAW_JSON"));
        assertTrue(fixture.report().narrative().strengths().stream().noneMatch(value -> value.contains("PRIVATE_RAW_JSON")));
        assertTrue(fixture.report().narrative().knowledgeGaps().stream().noneMatch(value -> value.contains("PRIVATE_RAW_JSON")));
        assertTrue(fixture.report().narrative().recommendedActions().stream().noneMatch(value -> value.contains("PRIVATE_RAW_JSON")));
        assertFalse(markdown.contains("PRIVATE_SNAPSHOT"));
        assertFalse(markdown.contains("PRIVATE_RAW_JSON"));
        assertOnlyLfLineBreaks(markdown);
    }

    private static FinalReport report() {
        TechnicalQuestion one = question("one", "# question one", "PRIVATE_EXPECTED_KEY", "PRIVATE_EVIDENCE_REASON");
        TechnicalQuestion two = question("two", "question two", "PRIVATE_EXPECTED_KEY", "PRIVATE_EVIDENCE_REASON");
        TechnicalQuestion three = question("three", "question three", "PRIVATE_EXPECTED_KEY", "PRIVATE_EVIDENCE_REASON");
        ProjectAnalysis analysis = new ProjectAnalysis("project", "Java CLI", "Project summary", List.of("start", "finish"),
                List.of(new ProjectComponent("app", "cli", "Runs commands", List.of("src/App.java"))), List.of("flow", "errors"), List.of(one, two, three));
        AnswerEvaluation partial = new AnswerEvaluation(Verdict.PARTIAL, 60, "feedback", List.of("flow"), List.of("errors"), Optional.of("follow up"));
        AnswerEvaluation correct = new AnswerEvaluation(Verdict.CORRECT, 90, "feedback", List.of("flow"), List.of(), Optional.empty());
        AnswerEvaluation skipped = AnswerEvaluation.skipped();
        InterviewTurn followUp = new InterviewTurn(TurnType.FOLLOW_UP, "follow up", "follow answer", correct);
        List<QuestionResult> results = List.of(
                new QuestionResult(1, one, new InterviewTurn(TurnType.PRIMARY, one.prompt(), "answer with ``` fence", partial), Optional.of(followUp), 75),
                new QuestionResult(2, two, new InterviewTurn(TurnType.PRIMARY, two.prompt(), "answer", skipped), Optional.empty(), 0),
                new QuestionResult(3, three, new InterviewTurn(TurnType.PRIMARY, three.prompt(), "answer", correct), Optional.empty(), 90));
        InterviewSession session = new InterviewSession("project", results, 73, Readiness.REVIEW_NEEDED, 1);
        ReportMetadata metadata = new ReportMetadata(Instant.EPOCH, "model", "project", "Java CLI", List.of(new AnalyzedFile("src/App.java", 3, false, 12)), 12, 0);
        ReportNarrative narrative = new ReportNarrative("Local report headline", "A sufficiently detailed local summary of the completed technical defense.", List.of("flow"), List.of("errors"), List.of("Review errors."));
        return new FinalReport(metadata, analysis, session, narrative, NarrativeSource.DETERMINISTIC_FALLBACK);
    }

    private static TechnicalQuestion question(String id, String prompt, String key, String reason) {
        return new TechnicalQuestion(id, prompt, "goal", List.of(key, "other"), List.of(new CodeEvidence("src/App.java", 1, 2, reason)));
    }

    private static NonRenderedInputs hostileReport() {
        String payload = "# [link](target) | table | \\";
        TechnicalQuestion one = question("one", "question" + payload, "PRIVATE_EXPECTED_KEY", "PRIVATE_EVIDENCE_REASON");
        TechnicalQuestion two = question("two", "two" + payload, "PRIVATE_EXPECTED_KEY", "PRIVATE_EVIDENCE_REASON");
        TechnicalQuestion three = question("three", "three" + payload, "PRIVATE_EXPECTED_KEY", "PRIVATE_EVIDENCE_REASON");
        ProjectAnalysis analysis = new ProjectAnalysis("project" + payload, "type" + payload, "summary" + payload,
                List.of("flow" + payload, "finish" + payload), List.of(new ProjectComponent("component" + payload, "cli", "responsibility" + payload, List.of("src/App.java"))),
                List.of("topic" + payload, "errors" + payload), List.of(one, two, three));
        AnswerEvaluation evaluation = new AnswerEvaluation(Verdict.PARTIAL, 50, "feedback" + payload,
                List.of("understood" + payload), List.of("missing" + payload), Optional.of("follow" + payload));
        List<QuestionResult> results = List.of(
                new QuestionResult(1, one, new InterviewTurn(TurnType.PRIMARY, one.prompt(), "answer " + payload, evaluation), Optional.empty(), 50),
                new QuestionResult(2, two, new InterviewTurn(TurnType.PRIMARY, two.prompt(), "answer " + payload, evaluation), Optional.empty(), 50),
                new QuestionResult(3, three, new InterviewTurn(TurnType.PRIMARY, three.prompt(), "answer " + payload, evaluation), Optional.empty(), 50));
        String snapshotContent = "PRIVATE_SNAPSHOT";
        ProjectSnapshot snapshot = new ProjectSnapshot(java.nio.file.Path.of("."), analysis.projectName(), analysis.projectType(),
                new ScanSummary(java.nio.file.Path.of("."), 0, 0, List.of()),
                List.of(new ProjectSnapshot.SelectedFile(java.nio.file.Path.of("zeta.md"), 1, false, 1),
                        new ProjectSnapshot.SelectedFile(java.nio.file.Path.of("alpha.md"), 1, false, 1)),
                snapshotContent, snapshotContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, 0);
        ReportMetadata metadata = ReportMetadata.from(snapshot, "model" + payload, Instant.EPOCH);
        String rawModelJson = """
                {"headline":"headline# [link](target) | table | \\\\",
                 "summary":"A sufficiently detailed summary # [link](target) | table | \\\\",
                 "strengths":["strength# [link](target) | table | \\\\"],
                 "knowledgeGaps":["gap# [link](target) | table | \\\\"],
                 "recommendedActions":["action# [link](target) | table | \\\\"],
                 "diagnostic":"PRIVATE_RAW_JSON"}""";
        ReportNarrative narrative = narrativeFromAllowedRawModelFields(rawModelJson);
        return new NonRenderedInputs(new FinalReport(metadata, analysis, new InterviewSession(analysis.projectName(), results, 50, Readiness.REVIEW_NEEDED, 0), narrative, NarrativeSource.AI),
                snapshot, rawModelJson);
    }

    private static ReportNarrative narrativeFromAllowedRawModelFields(String rawModelJson) {
        try {
            JsonNode response = new ObjectMapper().readTree(rawModelJson);
            return new ReportNarrative(response.required("headline").asText(), response.required("summary").asText(),
                    strings(response.required("strengths")), strings(response.required("knowledgeGaps")),
                    strings(response.required("recommendedActions")));
        } catch (Exception exception) {
            throw new AssertionError("Raw model fixture must be valid JSON", exception);
        }
    }

    private static List<String> strings(JsonNode values) {
        return StreamSupport.stream(values.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static void assertOnlyLfLineBreaks(String text) {
        assertTrue(text.codePoints().allMatch(MarkdownReportRendererTest::isAllowedCodePoint),
                () -> "Expected only LF line breaks: " + text);
    }

    private static boolean isAllowedCodePoint(int codePoint) {
        return codePoint == '\n' || !isLineBreak(codePoint);
    }

    private static boolean isLineBreak(int codePoint) {
        return codePoint == '\r' || codePoint == 0x000B || codePoint == 0x000C
                || (codePoint >= 0x001C && codePoint <= 0x001E) || codePoint == 0x0085
                || Character.getType(codePoint) == Character.LINE_SEPARATOR
                || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR;
    }

    private record NonRenderedInputs(FinalReport report, ProjectSnapshot snapshot, String rawModelJson) {
    }
}
