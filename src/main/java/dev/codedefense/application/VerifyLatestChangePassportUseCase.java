package dev.codedefense.application;

import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.PassportVerification;
import dev.codedefense.domain.StagedChangeIdentity;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.StoredPassportIdentity;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Read-only verification against a newly captured staged index. */
public final class VerifyLatestChangePassportUseCase {
    private final StagedChangeSource source; private final ChangePassportStore store;
    public VerifyLatestChangePassportUseCase(StagedChangeSource source, ChangePassportStore store) { this.source=Objects.requireNonNull(source,"source"); this.store=Objects.requireNonNull(store,"store"); }
    public Optional<PassportVerification> verify(Path repositoryPath) {
        return store.readLatestIdentity().map(identity -> new PassportVerification(identity.passport(),
                matches(identity, source.captureIdentity(repositoryPath)) ? PassportStatus.CURRENT : PassportStatus.EXPIRED));
    }
    private static boolean matches(StoredPassportIdentity identity, StagedChangeIdentity change) {
        return identity.repositoryIdentityHash().equals(change.repositoryIdentityHash())
                && identity.baseCommit().equals(change.baseCommit())
                && identity.indexTree().equals(change.indexTree())
                && identity.diffFingerprint().equals(change.diffFingerprint())
                && identity.changedPathHashes().equals(change.changedPathHashes())
                && change.hasStagedChanges();
    }
}
