package dev.codedefense.domain;

public enum Readiness {
    STRONG_UNDERSTANDING("Strong understanding"),
    REVIEW_NEEDED("Review needed"),
    KNOWLEDGE_GAPS("Knowledge gaps");

    private final String displayName;

    Readiness(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
