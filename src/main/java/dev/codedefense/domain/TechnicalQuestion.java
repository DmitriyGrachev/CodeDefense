package dev.codedefense.domain;

import java.util.List;
import java.util.Objects;

public record TechnicalQuestion(
        String id,
        String prompt,
        String learningGoal,
        List<String> expectedKeyPoints,
        List<CodeEvidence> evidence) {
    public TechnicalQuestion {
        id = CodeEvidence.requireNonBlank(id, "id");
        prompt = CodeEvidence.requireNonBlank(prompt, "prompt");
        learningGoal = CodeEvidence.requireNonBlank(learningGoal, "learningGoal");
        expectedKeyPoints = CodeEvidence.copyNonBlankStrings(expectedKeyPoints, "expectedKeyPoints");
        if (expectedKeyPoints.size() < 2 || expectedKeyPoints.size() > 6) {
            throw new IllegalArgumentException("expectedKeyPoints must contain between two and six entries");
        }
        Objects.requireNonNull(evidence, "evidence");
        evidence = List.copyOf(evidence);
        if (evidence.size() < 1 || evidence.size() > 3 || evidence.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("evidence must contain between one and three entries");
        }
    }
}
