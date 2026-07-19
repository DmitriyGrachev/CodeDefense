package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.PassportAttemptId;
import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.domain.StagedPassportGateReason;
import dev.codedefense.domain.StagedPassportGateResult;
import dev.codedefense.domain.StagedPassportGateState;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportTestFixtures;
import dev.codedefense.passport.StoredChangePassport;
import dev.codedefense.passport.StoredPassportIdentity;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EvaluateStagedPassportGateUseCaseTest {
    private static final Path REPOSITORY = Path.of(".").toAbsolutePath().normalize();
    private static final String REPOSITORY_HASH = "a".repeat(64);
    private static final String OTHER_REPOSITORY_HASH = "9".repeat(64);
    private static final String BASE = "b".repeat(40);
    private static final String INDEX = "c".repeat(64);
    private static final String FINGERPRINT = "d".repeat(64);

    @Test
    void returnsNoStagedChangeWithoutQueryingHistory() {
        RecordingSource source = RecordingSource.failure(new GitChangeException(GitChangeException.Kind.NO_STAGED_CHANGE));
        RecordingStore store = new RecordingStore(List.of());

        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(source, store).evaluate(REPOSITORY);

        assertState(result, StagedPassportGateState.NO_STAGED_CHANGE, StagedPassportGateReason.NO_INDEX_ENTRIES);
        assertEquals("", result.diffFingerprint());
        assertEquals(1, source.calls.get());
        assertEquals(0, store.calls.get());
    }

    @Test
    void returnsUndefendedWhenTheRepositoryHasNoStagedPassportHistory() {
        RecordingSource source = RecordingSource.success(change());
        RecordingStore store = new RecordingStore(List.of());

        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(source, store).evaluate(REPOSITORY);

        assertState(result, StagedPassportGateState.UNDEFENDED, StagedPassportGateReason.NO_STAGED_HISTORY);
        assertEquals(FINGERPRINT, result.diffFingerprint());
        assertEquals(2, result.stagedFileCount());
        assertEquals(1, source.calls.get());
        assertEquals(REPOSITORY_HASH, store.requestedRepositoryHash);
        assertEquals(ChangeKind.STAGED, store.requestedChangeKind);
        assertEquals(50, store.requestedLimit);
    }

    @Test
    void returnsCurrentOnlyForTheNewestExactStagedIdentity() {
        StoredChangePassport differentRepository = stored(ChangeKind.STAGED, OTHER_REPOSITORY_HASH,
                BASE, INDEX, FINGERPRINT, 9);
        StoredChangePassport commitWithSameFingerprint = stored(ChangeKind.COMMIT, REPOSITORY_HASH,
                BASE, "e".repeat(40), FINGERPRINT, 8);
        StoredChangePassport exact = stored(ChangeKind.STAGED, REPOSITORY_HASH,
                BASE, INDEX, FINGERPRINT, 3);
        RecordingSource source = RecordingSource.success(change());
        RecordingStore store = new RecordingStore(List.of(differentRepository, commitWithSameFingerprint, exact));

        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(source, store).evaluate(REPOSITORY);

        assertState(result, StagedPassportGateState.CURRENT, StagedPassportGateReason.IDENTITY_MATCH);
        assertEquals(3, result.attemptNumber());
        assertEquals(List.of(), result.relativePaths());
        assertEquals(1, source.calls.get());
    }

    @Test
    void commitReceiptWithTheSameFingerprintIsNotStagedHistory() {
        RecordingSource source = RecordingSource.success(change());
        RecordingStore store = new RecordingStore(List.of(stored(ChangeKind.COMMIT, REPOSITORY_HASH,
                BASE, "e".repeat(40), FINGERPRINT, 4)));

        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(source, store).evaluate(REPOSITORY);

        assertState(result, StagedPassportGateState.UNDEFENDED, StagedPassportGateReason.NO_STAGED_HISTORY);
        assertEquals(0, result.attemptNumber());
    }

    @Test
    void stagedReceiptFromADifferentRepositoryIsIgnored() {
        RecordingSource source = RecordingSource.success(change());
        RecordingStore store = new RecordingStore(List.of(stored(ChangeKind.STAGED, OTHER_REPOSITORY_HASH,
                BASE, INDEX, FINGERPRINT, 4)));

        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(source, store).evaluate(REPOSITORY);

        assertState(result, StagedPassportGateState.UNDEFENDED, StagedPassportGateReason.NO_STAGED_HISTORY);
        assertEquals(0, result.attemptNumber());
    }

    @Test
    void returnsExpiredWithBoundedPathsWhenNoReceiptHasTheExactIdentity() {
        RecordingSource source = RecordingSource.success(change());
        RecordingStore store = new RecordingStore(List.of(stored(ChangeKind.STAGED, REPOSITORY_HASH,
                BASE, "e".repeat(64), FINGERPRINT, 2)));

        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(source, store).evaluate(REPOSITORY);

        assertState(result, StagedPassportGateState.EXPIRED, StagedPassportGateReason.IDENTITY_CHANGED);
        assertEquals(0, result.attemptNumber());
        assertEquals(List.of("README.md", "src/App.java"), result.relativePaths());
        assertEquals(1, source.calls.get());
    }

    @Test
    void mapsInvalidRepositoryGitAndStoreFailuresWithoutLeakingSourceData() {
        assertUnavailable(new GitChangeException(GitChangeException.Kind.INVALID_REPOSITORY),
                StagedPassportGateReason.INVALID_REPOSITORY);
        assertUnavailable(new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED),
                StagedPassportGateReason.GIT_CAPTURE_FAILED);

        RecordingSource source = RecordingSource.success(change());
        RecordingStore store = new RecordingStore(List.of());
        store.failure = ChangePassportPersistenceException.readFailure();
        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(source, store).evaluate(REPOSITORY);
        assertState(result, StagedPassportGateState.UNAVAILABLE, StagedPassportGateReason.PASSPORT_STORE_FAILED);
        assertEquals("", result.diffFingerprint());
        assertEquals(0, result.stagedFileCount());
        assertEquals(List.of(), result.relativePaths());
    }

    @Test
    void doesNotCatchUnexpectedProgrammingErrors() {
        IllegalStateException failure = new IllegalStateException("programming bug");
        RecordingSource source = RecordingSource.failure(failure);
        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> new EvaluateStagedPassportGateUseCase(source, new RecordingStore(List.of()))
                        .evaluate(REPOSITORY)));
    }

    private static void assertUnavailable(RuntimeException failure, StagedPassportGateReason reason) {
        RecordingSource source = RecordingSource.failure(failure);
        StagedPassportGateResult result = new EvaluateStagedPassportGateUseCase(
                source, new RecordingStore(List.of())).evaluate(REPOSITORY);
        assertState(result, StagedPassportGateState.UNAVAILABLE, reason);
        assertEquals("", result.diffFingerprint());
        assertEquals(List.of(), result.relativePaths());
        assertEquals(1, source.calls.get());
    }

    private static void assertState(StagedPassportGateResult result, StagedPassportGateState state,
            StagedPassportGateReason reason) {
        assertEquals(1, result.protocolVersion());
        assertEquals(state, result.state());
        assertEquals(reason, result.reason());
    }

    private static StagedChange change() {
        return new StagedChange(REPOSITORY, REPOSITORY_HASH, BASE, INDEX, FINGERPRINT, List.of(
                new StagedChangeFile(Path.of("README.md"), StagedFileStatus.MODIFIED, 1, 0),
                new StagedChangeFile(Path.of("src/App.java"), StagedFileStatus.MODIFIED, 4, 2)), 5, 2);
    }

    private static StoredChangePassport stored(ChangeKind kind, String repositoryHash, String base,
            String sourceIdentity, String fingerprint, int attemptNumber) {
        PassportReceipt template = PassportReceipt.from(PassportTestFixtures.passport(PassportStatus.CURRENT),
                UUID.randomUUID().toString());
        String id = UUID.randomUUID().toString();
        Optional<PassportAttemptId> supersedes = attemptNumber == 1 ? Optional.empty()
                : Optional.of(new PassportAttemptId(UUID.randomUUID().toString()));
        PassportReceipt receipt = new PassportReceipt(template.schemaVersion(), id, repositoryHash, kind,
                base, sourceIdentity, fingerprint, Instant.EPOCH, template.statusAtCreation(), template.files(),
                template.categories(), template.overallScore(), template.readiness(), template.skippedPrimaryCount(),
                template.model(), new PassportAttemptId(id), supersedes, attemptNumber, template.focus());
        Path root = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Path markdown = root.resolve(id + ".md");
        return new StoredChangePassport(markdown, root.resolve(id + ".json"), receipt);
    }

    private static final class RecordingSource implements StagedChangeSource {
        private final StagedChange value;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingSource(StagedChange value, RuntimeException failure) {
            this.value = value;
            this.failure = failure;
        }

        static RecordingSource success(StagedChange value) { return new RecordingSource(value, null); }
        static RecordingSource failure(RuntimeException failure) { return new RecordingSource(null, failure); }

        @Override public CapturedStagedChange capture(Path requestedPath) { throw new AssertionError("not used"); }
        @Override public StagedChange inspect(Path requestedPath) {
            calls.incrementAndGet();
            if (failure != null) throw failure;
            return value;
        }
    }

    private static final class RecordingStore implements ChangePassportStore {
        private final List<StoredChangePassport> values;
        private final AtomicInteger calls = new AtomicInteger();
        private String requestedRepositoryHash;
        private ChangeKind requestedChangeKind;
        private int requestedLimit;
        private RuntimeException failure;

        private RecordingStore(List<StoredChangePassport> values) { this.values = values; }

        @Override public Path save(dev.codedefense.domain.ChangePassport passport) { throw new AssertionError("not used"); }
        @Override public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.empty(); }
        @Override public List<StoredChangePassport> listByRepository(String repositoryIdentityHash, int limit) {
            throw new AssertionError("repository-only query must not back the staged gate");
        }
        @Override public List<StoredChangePassport> listByRepositoryAndKind(String repositoryIdentityHash,
                ChangeKind changeKind, int limit) {
            calls.incrementAndGet();
            requestedRepositoryHash = repositoryIdentityHash;
            requestedChangeKind = changeKind;
            requestedLimit = limit;
            if (failure != null) throw failure;
            return values;
        }
    }
}
