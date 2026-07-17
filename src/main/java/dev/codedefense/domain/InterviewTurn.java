package dev.codedefense.domain;

import java.util.Objects;

public record InterviewTurn(
        TurnType type,
        String prompt,
        String answer,
        AnswerEvaluation evaluation) {
    public InterviewTurn {
        Objects.requireNonNull(type, "type");
        prompt = CodeEvidence.requireNonBlank(prompt, "prompt");
        answer = CodeEvidence.requireNonBlank(answer, "answer");
        Objects.requireNonNull(evaluation, "evaluation");
    }

    @Override
    public String toString() {
        return "InterviewTurn[type=%s, verdict=%s, score=%d]"
                .formatted(type, evaluation.verdict(), evaluation.score());
    }
}
