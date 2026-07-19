package dev.codedefense.jetbrains.ui;

public interface CodeDefenseToolWindowView {
    void setSessionActive(boolean active);
    void setConfirmationEnabled(boolean enabled);
    void showPreview(String value);
    void showConfirmation(String value);
    void showQuestion(String value);
    void showEvaluation(String value);
    void showQuestionScore(String value);
    void showSummary(String value);
    void showPassportSaved(String path, String value);
    void showCompleted(String value);
    void showError(String value);
    void clearAnswer();
    default void showPassportStatus(String value) { }
    default void showProvenance(String value) { }
    default void clearProvenanceConsent() { }
}
