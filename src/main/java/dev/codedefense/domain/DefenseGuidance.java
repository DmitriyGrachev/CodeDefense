package dev.codedefense.domain;

public record DefenseGuidance(String categoryId, String message) {
    public DefenseGuidance {
        categoryId = CodeEvidence.requireNonBlank(categoryId, "categoryId");
        message = CodeEvidence.requireNonBlank(message, "message");
        if (!java.util.Set.of("decision", "counterfactual", "test-prediction").contains(categoryId)) {
            throw new IllegalArgumentException("unknown category");
        }
    }
}
