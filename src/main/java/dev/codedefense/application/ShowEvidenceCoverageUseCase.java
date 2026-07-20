package dev.codedefense.application;

import dev.codedefense.change.GitChangeSource;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.RangeSelector;
import dev.codedefense.domain.StagedSelector;
import dev.codedefense.passport.ChangePassportStore;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class ShowEvidenceCoverageUseCase {
    private final GitChangeSource source;
    private final ChangePassportStore store;

    public ShowEvidenceCoverageUseCase(GitChangeSource source, ChangePassportStore store) {
        this.source = Objects.requireNonNull(source, "source");
        this.store = Objects.requireNonNull(store, "store");
    }

    public EvidenceCoverageView load(Path repository) {
        var latest = store.readLatest();
        if (latest.isEmpty()) {
            return new EvidenceCoverageView(Optional.empty(), Optional.empty(), "No coverage is available.");
        }
        var receipt = latest.orElseThrow().receipt();
        if (receipt.evidenceCoverage().isEmpty()) {
            return new EvidenceCoverageView(Optional.empty(), Optional.empty(),
                    "This Passport predates evidence coverage.");
        }
        ChangeSelector selector = switch (receipt.changeKind()) {
            case STAGED -> new StagedSelector();
            case COMMIT -> new CommitSelector(receipt.sourceIdentity());
            case RANGE -> new RangeSelector(receipt.baseCommit(), receipt.sourceIdentity());
        };
        var current = source.captureIdentity(repository, selector);
        boolean matches = current.kind() == receipt.changeKind()
                && current.baseCommit().equals(receipt.baseCommit())
                && current.targetIdentity().equals(receipt.sourceIdentity())
                && current.diffFingerprint().equals(receipt.diffFingerprint());
        if (!matches) {
            return new EvidenceCoverageView(receipt.evidenceCoverage(), Optional.empty(),
                    PassportStatus.EXPIRED.name());
        }
        var detail = store.readLatestCoverage().map(value -> value.coverage());
        return new EvidenceCoverageView(receipt.evidenceCoverage(), detail,
                detail.isPresent() ? PassportStatus.CURRENT.name() : "Detailed coverage unavailable.");
    }
}
