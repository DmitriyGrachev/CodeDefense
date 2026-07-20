package dev.codedefense.application;

/** Fixed, source-free failure at the repository learning-insights boundary. */
public final class RepositoryLearningInsightsException extends RuntimeException {
    private RepositoryLearningInsightsException() {
        super("Unable to build repository learning insights.");
    }

    public static RepositoryLearningInsightsException localFailure() {
        return new RepositoryLearningInsightsException();
    }
}
