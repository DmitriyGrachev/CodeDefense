package dev.codedefense.interview;

import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.*;
import java.util.*;
import java.util.regex.Pattern;

public final class AnswerEvaluationValidator {
    private static final String MESSAGE = "Codex returned an invalid answer evaluation.";
    private static final Pattern SPACE = Pattern.compile("\\s+");

    public AnswerEvaluation validate(Payload payload, AnswerEvaluationRequest request) {
        try {
            Objects.requireNonNull(payload); Objects.requireNonNull(request);
            Verdict verdict = Verdict.valueOf(payload.verdict());
            if (verdict == Verdict.SKIPPED || !bandMatches(verdict, payload.score())) throw invalid();
            String feedback = bounded(payload.feedback(), 10, 600);
            List<String> understood = concepts(payload.understoodConcepts());
            List<String> missing = concepts(payload.missingConcepts());
            Set<String> normalized = new HashSet<>();
            understood.forEach(value -> { if (!normalized.add(normalize(value))) throw invalid(); });
            missing.forEach(value -> { if (!normalized.add(normalize(value))) throw invalid(); });
            String followUp = payload.followUpQuestion() == null ? null : payload.followUpQuestion().strip();
            if (followUp == null || followUp.length() > 500 || (verdict == Verdict.CORRECT && !followUp.isEmpty())
                    || normalize(request.primaryQuestion().prompt()).equals(normalize(followUp))
                    || normalize(request.currentPrompt()).equals(normalize(followUp))) throw invalid();
            return new AnswerEvaluation(verdict, payload.score(), feedback, understood, missing,
                    followUp.isEmpty() ? Optional.empty() : Optional.of(followUp));
        } catch (InvalidCodexResponseException exception) { throw exception; }
        catch (RuntimeException exception) { throw invalid(); }
    }
    private static boolean bandMatches(Verdict verdict, int score) {
        return switch (verdict) { case CORRECT -> score >= 80 && score <= 100; case PARTIAL -> score >= 40 && score <= 79; case INCORRECT -> score >= 0 && score <= 39; default -> false; };
    }
    private static List<String> concepts(List<String> values) {
        if (values == null || values.size() > 6) throw invalid();
        List<String> result = new ArrayList<>(); for (String value : values) result.add(boundedCanonical(value, 2, 200)); return List.copyOf(result);
    }
    private static String bounded(String value, int min, int max) { if (value == null) throw invalid(); String v=value.strip(); if(v.length()<min||v.length()>max) throw invalid(); return v; }
    private static String boundedCanonical(String value, int min, int max) { String v=bounded(value,min,max); return SPACE.matcher(v).replaceAll(" "); }
    private static String normalize(String value) { return SPACE.matcher(value.strip()).replaceAll(" ").toLowerCase(Locale.ROOT); }
    private static InvalidCodexResponseException invalid() { return new InvalidCodexResponseException(MESSAGE); }

    public record Payload(String verdict, int score, String feedback, List<String> understoodConcepts,
                          List<String> missingConcepts, String followUpQuestion) {}
}
