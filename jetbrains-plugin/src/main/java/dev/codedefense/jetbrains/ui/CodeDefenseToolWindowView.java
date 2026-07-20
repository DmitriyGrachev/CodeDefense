package dev.codedefense.jetbrains.ui;

import dev.codedefense.jetbrains.gate.StagedGateView;
import dev.codedefense.jetbrains.evidence.EvidenceNavigator;
import dev.codedefense.jetbrains.process.EvidenceLocationView;
import dev.codedefense.jetbrains.insights.RepositoryInsightsView;
import dev.codedefense.jetbrains.evidence.EvidenceCoverageView;
import java.util.List;
import java.util.function.Function;

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
    default void setRetryAvailable(boolean available) { }
    default void showPassportStatus(String value) { }
    default void showProvenance(String value) { }
    default void clearProvenanceConsent() { }
    default void showGateStatus(StagedGateView value) { }
    default void prepareStagedDefense() { }
    default void showEvidence(List<EvidenceLocationView> locations,
            Function<EvidenceLocationView, EvidenceNavigator.NavigationResult> opener) { }
    default void clearEvidence() { }
    default void showEvidenceCoverage(EvidenceCoverageView coverage,
            Function<EvidenceLocationView, EvidenceNavigator.NavigationResult> opener) { }
    default void clearEvidenceCoverage() { }
    default void showRepositoryInsights(RepositoryInsightsView value) { }
    default void showRepositoryInsightsUnavailable() { }
}
