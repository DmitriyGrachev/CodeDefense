package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.PassportAttemptId;
import dev.codedefense.domain.PassportCategoryReceipt;
import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.RepositoryLearningInsights;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeIdentity;
import dev.codedefense.domain.Verdict;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportTestFixtures;
import dev.codedefense.passport.StoredChangePassport;
import dev.codedefense.passport.StoredPassportIdentity;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BuildRepositoryLearningInsightsUseCaseTest {
    private static final Path REPOSITORY = Path.of("C:/private/repository")
            .toAbsolutePath().normalize();
    private static final String REPOSITORY_HASH = "a".repeat(64);
    private static final String OTHER_REPOSITORY_HASH = "9".repeat(64);

    @Test
    void emptyMatchingHistoryUsesZeroAveragesAndNoLabels() {
        RecordingSource source = new RecordingSource(identity(false));
        RecordingStore store = new RecordingStore(List.of());

        RepositoryLearningInsights result = useCase(source, store).build(REPOSITORY, 20);

        assertEquals(0, result.attemptCount());
        assertEquals(0, result.defendedChangeCount());
        assertEquals(List.of(0, 0, 0),
                result.categories().stream().map(value -> value.averageScore()).toList());
        assertEquals("", result.strongestCategory());
        assertEquals("", result.practiceCategory());
        assertEquals(List.of(), result.recentOverallScores());
        assertEquals(1, source.calls.get());
        assertFalse(source.lastIdentity.hasStagedChanges());
    }

    @Test
    void aggregatesAllChangeKindsUsingStoredScoresAndFiltersRepositoryDefensively() {
        List<StoredChangePassport> newestFirst = List.of(
                stored(REPOSITORY_HASH, ChangeKind.RANGE, "3".repeat(64), 100, 30, 0, 43),
                stored(OTHER_REPOSITORY_HASH, ChangeKind.STAGED, "8".repeat(64), 100, 100, 100, 100),
                stored(REPOSITORY_HASH, ChangeKind.COMMIT, "2".repeat(64), 51, 60, 0, 37),
                stored(REPOSITORY_HASH, ChangeKind.STAGED, "1".repeat(64), 0, 0, 0, 0));
        RecordingStore store = new RecordingStore(newestFirst);

        RepositoryLearningInsights result = useCase(new RecordingSource(identity(true)), store)
                .build(REPOSITORY, 20);

        assertEquals(3, result.attemptCount());
        assertEquals(3, result.defendedChangeCount());
        assertEquals(List.of("decision", "counterfactual", "test-prediction"),
                result.categories().stream().map(value -> value.id()).toList());
        assertEquals(List.of(50, 30, 0),
                result.categories().stream().map(value -> value.averageScore()).toList());
        assertEquals("decision", result.strongestCategory());
        assertEquals("test-prediction", result.practiceCategory());
        assertEquals(List.of(0, 37, 43), result.recentOverallScores());
        assertEquals(REPOSITORY_HASH, store.repositoryHash);
        assertEquals(20, store.limit);
    }

    @Test
    void roundsAveragesAndCountsDistinctFullFingerprints() {
        RepositoryLearningInsights result = useCase(new RecordingSource(identity(false)),
                new RecordingStore(List.of(
                        stored(REPOSITORY_HASH, ChangeKind.STAGED, "1".repeat(64), 50, 10, 0, 20),
                        stored(REPOSITORY_HASH, ChangeKind.COMMIT, "1".repeat(64), 51, 11, 1, 21))))
                .build(REPOSITORY, 20);

        assertEquals(List.of(51, 11, 1),
                result.categories().stream().map(value -> value.averageScore()).toList());
        assertEquals(1, result.defendedChangeCount());
    }

    @Test
    void stableCategoryOrderBreaksStrongestAndPracticeTies() {
        RepositoryLearningInsights result = useCase(new RecordingSource(identity(false)),
                new RecordingStore(List.of(stored(REPOSITORY_HASH, ChangeKind.STAGED,
                        "1".repeat(64), 50, 50, 50, 50))))
                .build(REPOSITORY, 20);

        assertEquals("decision", result.strongestCategory());
        assertEquals("decision", result.practiceCategory());
    }

    @Test
    void limitsBeforeAggregationAndKeepsTheNewestTenScoresChronological() {
        ArrayList<StoredChangePassport> newestFirst = new ArrayList<>();
        for (int score = 15; score >= 1; score--) {
            newestFirst.add(stored(REPOSITORY_HASH, ChangeKind.STAGED,
                    "%064x".formatted(score), score, score, score, score));
        }
        RecordingStore store = new RecordingStore(newestFirst);

        RepositoryLearningInsights result = useCase(new RecordingSource(identity(false)), store)
                .build(REPOSITORY, 12);

        assertEquals(12, result.attemptCount());
        assertEquals(12, result.defendedChangeCount());
        assertEquals(List.of(6, 7, 8, 9, 10, 11, 12, 13, 14, 15), result.recentOverallScores());
        assertEquals(12, store.limit);
    }

    @Test
    void validatesRequestedLimitAndEnforcesItAgainstAnOverReturningStore() {
        BuildRepositoryLearningInsightsUseCase useCase = useCase(
                new RecordingSource(identity(false)), new RecordingStore(List.of()));
        assertThrows(IllegalArgumentException.class, () -> useCase.build(REPOSITORY, 0));
        assertThrows(IllegalArgumentException.class, () -> useCase.build(REPOSITORY, 21));

        ArrayList<StoredChangePassport> values = new ArrayList<>();
        for (int score = 0; score < 25; score++) {
            values.add(stored(REPOSITORY_HASH, ChangeKind.STAGED,
                    "%064x".formatted(score + 1), score, score, score, score));
        }
        RecordingStore oneStore = new RecordingStore(values);
        RepositoryLearningInsights one = useCase(new RecordingSource(identity(false)), oneStore)
                .build(REPOSITORY, 1);
        assertEquals(1, one.attemptCount());
        assertEquals(1, oneStore.limit);

        RepositoryLearningInsights result = useCase(new RecordingSource(identity(false)),
                new RecordingStore(values)).build(REPOSITORY, 20);
        assertEquals(20, result.attemptCount());
    }

    @Test
    void mapsGitAndStoreFailuresToOneTypedSourceFreeFailure() {
        assertSafeFailure(new RecordingSource(new GitChangeException(
                GitChangeException.Kind.INVALID_REPOSITORY)), new RecordingStore(List.of()));
        RecordingStore failingStore = new RecordingStore(List.of());
        failingStore.failure = ChangePassportPersistenceException.readFailure();
        assertSafeFailure(new RecordingSource(identity(false)), failingStore);
    }

    @Test
    void unexpectedProgrammingFailuresRemainVisible() {
        IllegalStateException failure = new IllegalStateException("programming bug");
        RecordingStore store = new RecordingStore(List.of());
        store.failure = failure;

        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> useCase(new RecordingSource(identity(false)), store).build(REPOSITORY, 20)));
    }

    private static void assertSafeFailure(RecordingSource source, RecordingStore store) {
        RepositoryLearningInsightsException exception = assertThrows(
                RepositoryLearningInsightsException.class,
                () -> useCase(source, store).build(REPOSITORY, 20));
        assertEquals("Unable to build repository learning insights.", exception.getMessage());
        assertFalse(exception.getMessage().contains(REPOSITORY.toString()));
        assertFalse(exception.getMessage().contains(REPOSITORY_HASH));
        assertTrue(exception.getCause() == null);
    }

    private static BuildRepositoryLearningInsightsUseCase useCase(
            RecordingSource source, RecordingStore store) {
        return new BuildRepositoryLearningInsightsUseCase(source, store);
    }

    private static StagedChangeIdentity identity(boolean staged) {
        return new StagedChangeIdentity(REPOSITORY, REPOSITORY_HASH, "b".repeat(40),
                "c".repeat(64), "d".repeat(64),
                staged ? List.of("e".repeat(64)) : List.of());
    }

    private static StoredChangePassport stored(String repositoryHash, ChangeKind kind,
            String fingerprint, int decision, int counterfactual, int testPrediction,
            int overall) {
        PassportReceipt template = PassportReceipt.from(
                PassportTestFixtures.passport(PassportStatus.CURRENT), UUID.randomUUID().toString());
        String id = UUID.randomUUID().toString();
        List<PassportCategoryReceipt> categories = List.of(
                category("decision", decision),
                category("counterfactual", counterfactual),
                category("test-prediction", testPrediction));
        String source = kind == ChangeKind.STAGED ? "c".repeat(64) : "e".repeat(40);
        PassportReceipt receipt = new PassportReceipt(3, id, repositoryHash, kind,
                template.baseCommit(), source, fingerprint, Instant.EPOCH,
                template.statusAtCreation(), template.files(), categories, overall,
                template.readiness(), 0, template.model(), new PassportAttemptId(id),
                Optional.empty(), 1, template.focus());
        Path root = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        return new StoredChangePassport(root.resolve(id + ".md"), root.resolve(id + ".json"), receipt);
    }

    private static PassportCategoryReceipt category(String id, int score) {
        Verdict verdict = score == 0 ? Verdict.SKIPPED : Verdict.PARTIAL;
        return new PassportCategoryReceipt(id, verdict, score, Optional.empty(), Optional.empty(), score);
    }

    private static final class RecordingSource implements StagedChangeSource {
        private final StagedChangeIdentity identity;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();
        private StagedChangeIdentity lastIdentity;

        RecordingSource(StagedChangeIdentity identity) { this.identity = identity; this.failure = null; }
        RecordingSource(RuntimeException failure) { this.identity = null; this.failure = failure; }

        @Override public CapturedStagedChange capture(Path requestedPath) {
            throw new AssertionError("capture must not be used");
        }
        @Override public StagedChange inspect(Path requestedPath) {
            throw new AssertionError("inspect must not be used");
        }
        @Override public StagedChangeIdentity captureIdentity(Path requestedPath) {
            calls.incrementAndGet();
            if (failure != null) throw failure;
            lastIdentity = identity;
            return identity;
        }
    }

    private static final class RecordingStore implements ChangePassportStore {
        private final List<StoredChangePassport> values;
        private RuntimeException failure;
        private String repositoryHash;
        private int limit;

        RecordingStore(List<StoredChangePassport> values) { this.values = values; }

        @Override public Path save(dev.codedefense.domain.ChangePassport passport) {
            throw new AssertionError("save must not be used");
        }
        @Override public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.empty(); }
        @Override public List<StoredChangePassport> listByRepository(
                String repositoryIdentityHash, int requestedLimit) {
            repositoryHash = repositoryIdentityHash;
            limit = requestedLimit;
            if (failure != null) throw failure;
            return values;
        }
    }
}
