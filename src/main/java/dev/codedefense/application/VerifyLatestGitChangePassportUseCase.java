package dev.codedefense.application;

import dev.codedefense.change.GitChangeSource;
import dev.codedefense.domain.*;
import dev.codedefense.passport.ChangePassportStore;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class VerifyLatestGitChangePassportUseCase implements ChangePassportVerifier {
    private final GitChangeSource source;
    private final ChangePassportStore store;
    public VerifyLatestGitChangePassportUseCase(GitChangeSource source, ChangePassportStore store) {
        this.source = Objects.requireNonNull(source); this.store = Objects.requireNonNull(store);
    }
    @Override public Optional<PassportVerification> verify(Path repository) {
        return store.readLatest().map(stored -> {
            var receipt = stored.receipt();
            ChangeSelector selector = switch (receipt.changeKind()) {
                case STAGED -> new StagedSelector();
                case COMMIT -> new CommitSelector(receipt.sourceIdentity());
                case RANGE -> new RangeSelector(receipt.baseCommit(), receipt.sourceIdentity());
            };
            GitChangeIdentity current = source.captureIdentity(repository, selector);
            boolean match = current.kind() == receipt.changeKind()
                    && current.baseCommit().equals(receipt.baseCommit())
                    && current.targetIdentity().equals(receipt.sourceIdentity())
                    && current.diffFingerprint().equals(receipt.diffFingerprint());
            return new PassportVerification(stored.markdownPath(),
                    match ? PassportStatus.CURRENT : PassportStatus.EXPIRED);
        });
    }
}
