package dev.codedefense.report;

import dev.codedefense.domain.AnalyzedFile;
import dev.codedefense.domain.FinalReport;
import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.ReportNarrative;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/** Pure, deterministic Markdown rendering for a completed local report. */
public final class MarkdownReportRenderer {
    public String render(FinalReport report) {
        Objects.requireNonNull(report, "report");
        StringBuilder markdown = new StringBuilder();
        line(markdown, "# CodeDefense Understanding Report");
        line(markdown, "");
        line(markdown, "## Project");
        bullet(markdown, "Project", report.metadata().projectName());
        bullet(markdown, "Type", report.metadata().projectType());
        bullet(markdown, "Analyzed at", DateTimeFormatter.ISO_INSTANT.format(report.metadata().analyzedAt()));
        bullet(markdown, "Model", report.metadata().model());
        bullet(markdown, "Snapshot size", report.metadata().snapshotBytes() + " bytes");
        bullet(markdown, "Redactions", Integer.toString(report.metadata().redactionCount()));
        line(markdown, "");
        line(markdown, "## Local assessment");
        bullet(markdown, "Overall score", report.session().overallScore() + "/100");
        bullet(markdown, "Readiness", report.session().readiness().displayName());
        bullet(markdown, "Skipped primary questions", Integer.toString(report.session().skippedQuestionCount()));
        line(markdown, "");
        appendNarrative(markdown, report.narrative());
        if (report.narrativeSource().name().equals("DETERMINISTIC_FALLBACK")) {
            line(markdown, "> Generated with the local deterministic fallback; no narrative AI response was used.");
            line(markdown, "");
        }
        for (QuestionResult result : report.session().results()) {
            appendQuestion(markdown, result);
        }
        line(markdown, "## Analyzed files");
        for (AnalyzedFile file : report.metadata().selectedFiles()) {
            line(markdown, "- " + MarkdownTextEscaper.inline(file.path()) + " (" + file.includedLines() + " lines, "
                    + file.renderedBytes() + " bytes" + (file.truncated() ? ", truncated" : "") + ")");
        }
        line(markdown, "");
        line(markdown, "## Privacy");
        line(markdown, "This report includes validated feedback and concepts, while omitting source snapshots, raw model JSON, expected key points, and evidence reasons.");
        return markdown.toString();
    }

    private static void appendNarrative(StringBuilder markdown, ReportNarrative narrative) {
        line(markdown, "## Narrative");
        line(markdown, MarkdownTextEscaper.inline(narrative.headline()));
        line(markdown, "");
        line(markdown, MarkdownTextEscaper.inline(narrative.summary()));
        appendList(markdown, "### Strengths", narrative.strengths(), "No strengths were recorded.");
        appendList(markdown, "### Knowledge gaps", narrative.knowledgeGaps(), "No knowledge gaps were recorded.");
        appendList(markdown, "### Recommended actions", narrative.recommendedActions(), "No recommended actions were recorded.");
    }

    private static void appendQuestion(StringBuilder markdown, QuestionResult result) {
        line(markdown, "## Question " + result.questionNumber());
        appendTurn(markdown, result.primaryTurn(), "### Primary answer");
        if (result.primaryTurn().evaluation().verdict().name().equals("SKIPPED")) {
            line(markdown, "Skipped by user.");
            line(markdown, "");
        }
        line(markdown, "- Local final score: " + result.finalScore() + "/100");
        line(markdown, "");
        result.followUpTurn().ifPresent(turn -> appendTurn(markdown, turn, "### Follow-up"));
    }

    private static void appendTurn(StringBuilder markdown, InterviewTurn turn, String heading) {
        line(markdown, heading);
        line(markdown, "Question: " + MarkdownTextEscaper.inline(turn.prompt()));
        line(markdown, "Answer:");
        line(markdown, MarkdownTextEscaper.fencedText(turn.answer()));
        line(markdown, "- Verdict: " + MarkdownTextEscaper.inline(turn.evaluation().verdict().name()));
        line(markdown, "- Evaluation score: " + turn.evaluation().score() + "/100");
        line(markdown, "- Feedback: " + MarkdownTextEscaper.inline(turn.evaluation().feedback()));
        appendConcepts(markdown, "Understood concepts", turn.evaluation().understoodConcepts());
        appendConcepts(markdown, "Knowledge gaps", turn.evaluation().missingConcepts());
        line(markdown, "");
    }

    private static void appendConcepts(StringBuilder markdown, String label, List<String> concepts) {
        if (!concepts.isEmpty()) {
            line(markdown, "- " + label + ": " + concepts.stream().map(MarkdownTextEscaper::inline).reduce((left, right) -> left + ", " + right).orElse(""));
        }
    }

    private static void appendList(StringBuilder markdown, String heading, List<String> values, String emptyMessage) {
        line(markdown, heading);
        if (values.isEmpty()) {
            line(markdown, emptyMessage);
        } else {
            for (String value : values) {
                line(markdown, "- " + MarkdownTextEscaper.inline(value));
            }
        }
        line(markdown, "");
    }

    private static void bullet(StringBuilder markdown, String label, String value) {
        line(markdown, "- " + label + ": " + MarkdownTextEscaper.inline(value));
    }

    private static void line(StringBuilder markdown, String value) {
        markdown.append(value).append('\n');
    }
}
