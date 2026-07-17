package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinalReportTest {
    @Test
    void validatesCrossRecordIdentityAndKeepsToStringPrivate() {
        ProjectAnalysis analysis = ReportGenerationRequestTest.analysis("project", List.of("q1", "q2", "q3"));
        InterviewSession session = ReportGenerationRequestTest.session("project", List.of("q1", "q2", "q3"));
        ReportMetadata metadata = new ReportMetadata(Instant.now(), "model", "project", "Java", List.of(new AnalyzedFile("src/App.java", 2, false, 3)), 3, 0);
        ReportNarrative narrative = new ReportNarrative("A valid headline", "x".repeat(40), List.of(), List.of(), List.of("action"));

        FinalReport report = assertDoesNotThrow(() -> new FinalReport(metadata, analysis, session, narrative, NarrativeSource.AI));
        assertFalse(report.toString().contains("ANSWER_SECRET"));
        assertFalse(report.toString().contains("QUESTION_PROMPT_SECRET"));
        assertFalse(report.toString().contains("FEEDBACK_SECRET"));
        assertThrows(IllegalArgumentException.class, () -> new FinalReport(
                new ReportMetadata(Instant.now(), "model", "other", "Java", List.of(), 0, 0), analysis, session, narrative, NarrativeSource.AI));
    }
}
