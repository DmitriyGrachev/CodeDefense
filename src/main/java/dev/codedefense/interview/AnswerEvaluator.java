package dev.codedefense.interview;

import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.AnswerEvaluationRequest;

@FunctionalInterface
public interface AnswerEvaluator {
    AnswerEvaluation evaluate(AnswerEvaluationRequest request);
}
