package dev.codedefense.report;

import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.ReportGenerationRequest;
import dev.codedefense.domain.ReportNarrative;
import dev.codedefense.domain.Verdict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Produces a local report narrative when the optional narrative request is unavailable. */
public final class DeterministicReportNarrativeFactory {
    private static final int MAXIMUM_ITEMS = 6;
    private static final String DEFAULT_ACTION = "Review the project flow and explain the main design choices.";

    public ReportNarrative create(ReportGenerationRequest request) {
        Objects.requireNonNull(request, "request");
        List<String> strengths = firstSeen(request.session().results(), true);
        List<String> gaps = firstSeen(request.session().results(), false);
        List<String> actions = actions(request, gaps);
        int skipped = request.session().skippedQuestionCount();
        String summary = "This locally generated summary reflects the completed technical defense, including %d skipped question%s."
                .formatted(skipped, skipped == 1 ? "" : "s");
        return new ReportNarrative("Local understanding report", summary, strengths, gaps, actions);
    }

    public ReportNarrative generate(ReportGenerationRequest request) {
        return create(request);
    }

    private static List<String> firstSeen(List<QuestionResult> results, boolean understood) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (QuestionResult result : results) {
            add(values, concepts(result.primaryTurn().evaluation(), understood));
            result.followUpTurn().ifPresent(turn -> add(values, concepts(turn.evaluation(), understood)));
        }
        return List.copyOf(values.values());
    }

    private static List<String> concepts(AnswerEvaluation evaluation, boolean understood) {
        return understood ? evaluation.understoodConcepts() : evaluation.missingConcepts();
    }

    private static void add(LinkedHashMap<String, String> values, List<String> candidates) {
        for (String candidate : candidates) {
            String normalized = candidate.replaceAll("\\s+", " ").strip();
            String key = normalized.toLowerCase(Locale.ROOT);
            if (values.size() < MAXIMUM_ITEMS && normalized.length() >= 3) {
                values.putIfAbsent(key, normalized);
            }
        }
    }

    private static List<String> actions(ReportGenerationRequest request, List<String> gaps) {
        List<String> actions = new ArrayList<>();
        for (String gap : gaps) {
            addAction(actions, "Review " + gap + ".");
        }
        for (QuestionResult result : request.session().results()) {
            if (result.primaryTurn().evaluation().verdict() == Verdict.SKIPPED) {
                addAction(actions, "Return to question %d and explain its design trade-offs.".formatted(result.questionNumber()));
            }
        }
        if (actions.isEmpty()) {
            actions.add(DEFAULT_ACTION);
        }
        return List.copyOf(actions);
    }

    private static void addAction(List<String> actions, String action) {
        if (actions.size() < MAXIMUM_ITEMS) {
            actions.add(action);
        }
    }
}
