package dev.codedefense.application;

import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.CategoryLearningInsight;
import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.RepositoryLearningInsights;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.StoredChangePassport;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds bounded Java-owned learning aggregates without reading Passport Markdown. */
public final class BuildRepositoryLearningInsightsUseCase {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAXIMUM_ATTEMPTS = 20;
    private static final int MAXIMUM_RECENT_SCORES = 10;
    private static final List<String> CATEGORY_IDS =
            List.of("decision", "counterfactual", "test-prediction");

    private final StagedChangeSource source;
    private final ChangePassportStore store;

    public BuildRepositoryLearningInsightsUseCase(
            StagedChangeSource source, ChangePassportStore store) {
        this.source = Objects.requireNonNull(source, "source");
        this.store = Objects.requireNonNull(store, "store");
    }

    public RepositoryLearningInsights build(Path repository, int limit) {
        Objects.requireNonNull(repository, "repository");
        if (limit < 1 || limit > MAXIMUM_ATTEMPTS) {
            throw new IllegalArgumentException("limit must be between 1 and 20");
        }

        try {
            String repositoryHash = source.captureIdentity(repository).repositoryIdentityHash();
            List<StoredChangePassport> history = Objects.requireNonNull(
                    store.listByRepository(repositoryHash, limit), "store result");
            return aggregate(repositoryHash, history, limit);
        } catch (GitChangeException | ChangePassportPersistenceException exception) {
            throw RepositoryLearningInsightsException.localFailure();
        }
    }

    private static RepositoryLearningInsights aggregate(
            String repositoryHash, List<StoredChangePassport> history, int limit) {
        int[] sums = new int[CATEGORY_IDS.size()];
        int attempts = 0;
        Set<String> fingerprints = new HashSet<>();
        ArrayDeque<Integer> recent = new ArrayDeque<>(MAXIMUM_RECENT_SCORES);

        for (StoredChangePassport stored : history) {
            PassportReceipt receipt = Objects.requireNonNull(stored, "stored Passport").receipt();
            if (!receipt.repositoryIdentityHash().equals(repositoryHash)) {
                continue;
            }
            if (attempts == limit) {
                break;
            }
            for (int index = 0; index < CATEGORY_IDS.size(); index++) {
                sums[index] += receipt.categories().get(index).finalScore();
            }
            fingerprints.add(receipt.diffFingerprint());
            if (recent.size() < MAXIMUM_RECENT_SCORES) {
                recent.addFirst(receipt.overallScore());
            }
            attempts++;
        }

        List<CategoryLearningInsight> categories = new ArrayList<>(CATEGORY_IDS.size());
        for (int index = 0; index < CATEGORY_IDS.size(); index++) {
            int average = attempts == 0 ? 0 : (int) Math.round(sums[index] / (double) attempts);
            categories.add(new CategoryLearningInsight(CATEGORY_IDS.get(index), average));
        }

        String strongest = attempts == 0 ? "" : categoryAtExtreme(categories, true);
        String practice = attempts == 0 ? "" : categoryAtExtreme(categories, false);
        return new RepositoryLearningInsights(SCHEMA_VERSION, attempts, fingerprints.size(),
                categories, strongest, practice, List.copyOf(recent));
    }

    private static String categoryAtExtreme(
            List<CategoryLearningInsight> categories, boolean maximum) {
        CategoryLearningInsight selected = categories.getFirst();
        for (int index = 1; index < categories.size(); index++) {
            CategoryLearningInsight candidate = categories.get(index);
            if (maximum ? candidate.averageScore() > selected.averageScore()
                    : candidate.averageScore() < selected.averageScore()) {
                selected = candidate;
            }
        }
        return selected.id();
    }
}
