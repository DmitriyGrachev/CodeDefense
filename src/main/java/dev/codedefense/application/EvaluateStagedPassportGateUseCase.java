package dev.codedefense.application;

import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedPassportGateReason;
import dev.codedefense.domain.StagedPassportGateResult;
import dev.codedefense.domain.StagedPassportGateState;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.StoredChangePassport;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class EvaluateStagedPassportGateUseCase {
    private static final int PROTOCOL_VERSION = 1;
    private static final int HISTORY_LIMIT = 50;
    private static final int PATH_LIMIT = 30;
    private final StagedChangeSource source;
    private final ChangePassportStore store;

    public EvaluateStagedPassportGateUseCase(StagedChangeSource source, ChangePassportStore store) {
        this.source = Objects.requireNonNull(source, "source");
        this.store = Objects.requireNonNull(store, "store");
    }

    public StagedPassportGateResult evaluate(Path repository) {
        Objects.requireNonNull(repository, "repository");
        StagedChange change;
        try {
            change = Objects.requireNonNull(source.inspect(repository), "source result");
        } catch (GitChangeException exception) {
            return sourceFailure(exception.kind());
        }

        List<StoredChangePassport> repositoryHistory;
        try {
            repositoryHistory = Objects.requireNonNull(
                    store.listByRepositoryAndKind(change.repositoryIdentityHash(), ChangeKind.STAGED,
                            HISTORY_LIMIT), "store result");
        } catch (ChangePassportPersistenceException exception) {
            return unavailable(StagedPassportGateReason.PASSPORT_STORE_FAILED);
        }

        List<PassportReceipt> stagedHistory = repositoryHistory.stream()
                .map(StoredChangePassport::receipt)
                .filter(receipt -> receipt.repositoryIdentityHash().equals(change.repositoryIdentityHash()))
                .filter(receipt -> receipt.changeKind() == ChangeKind.STAGED)
                .toList();
        if (stagedHistory.isEmpty()) {
            return withMetadata(change, StagedPassportGateState.UNDEFENDED,
                    StagedPassportGateReason.NO_STAGED_HISTORY, 0, List.of());
        }

        return stagedHistory.stream()
                .filter(receipt -> matches(receipt, change))
                .findFirst()
                .map(receipt -> withMetadata(change, StagedPassportGateState.CURRENT,
                        StagedPassportGateReason.IDENTITY_MATCH, receipt.attemptNumber(), List.of()))
                .orElseGet(() -> withMetadata(change, StagedPassportGateState.EXPIRED,
                        StagedPassportGateReason.IDENTITY_CHANGED, 0, change.files().stream()
                                .map(file -> file.path().toString().replace('\\', '/'))
                                .limit(PATH_LIMIT).toList()));
    }

    private static boolean matches(PassportReceipt receipt, StagedChange change) {
        return receipt.baseCommit().equals(change.baseCommit())
                && receipt.sourceIdentity().equals(change.indexIdentity())
                && receipt.diffFingerprint().equals(change.diffFingerprint());
    }

    private static StagedPassportGateResult sourceFailure(GitChangeException.Kind kind) {
        return switch (kind) {
            case NO_STAGED_CHANGE -> new StagedPassportGateResult(PROTOCOL_VERSION,
                    StagedPassportGateState.NO_STAGED_CHANGE, StagedPassportGateReason.NO_INDEX_ENTRIES,
                    "", 0, 0, 0, 0, List.of());
            case INVALID_REPOSITORY -> unavailable(StagedPassportGateReason.INVALID_REPOSITORY);
            default -> unavailable(StagedPassportGateReason.GIT_CAPTURE_FAILED);
        };
    }

    private static StagedPassportGateResult unavailable(StagedPassportGateReason reason) {
        return new StagedPassportGateResult(PROTOCOL_VERSION, StagedPassportGateState.UNAVAILABLE,
                reason, "", 0, 0, 0, 0, List.of());
    }

    private static StagedPassportGateResult withMetadata(StagedChange change,
            StagedPassportGateState state, StagedPassportGateReason reason, int attemptNumber,
            List<String> paths) {
        return new StagedPassportGateResult(PROTOCOL_VERSION, state, reason, change.diffFingerprint(),
                attemptNumber, change.files().size(), change.addedLines(), change.deletedLines(), paths);
    }
}
