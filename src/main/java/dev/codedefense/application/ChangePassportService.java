package dev.codedefense.application;

import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.StoredPassportIdentity;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Re-captures immediately before persistence so an index race is recorded as expired. */
public final class ChangePassportService {
    private final StagedChangeSource source; private final ChangePassportStore store; private final Clock clock; private final String model;
    public ChangePassportService(StagedChangeSource source, ChangePassportStore store, Clock clock, String model) { this.source=Objects.requireNonNull(source,"source"); this.store=Objects.requireNonNull(store,"store"); this.clock=Objects.requireNonNull(clock,"clock"); this.model=Objects.requireNonNull(model,"model"); }
    public Path createAndSave(StagedChange beforeInterview, ProjectAnalysis analysis, InterviewSession session) {
        Objects.requireNonNull(beforeInterview,"beforeInterview");
        StagedChange recaptured = source.capture(beforeInterview.repositoryRoot()).change();
        PassportStatus status = same(beforeInterview, recaptured) ? PassportStatus.CURRENT : PassportStatus.EXPIRED;
        return store.save(new ChangePassport(beforeInterview, analysis, session, Instant.now(clock), model, status));
    }
    static boolean same(StagedChange left, StagedChange right) {
        return left.repositoryIdentityHash().equals(right.repositoryIdentityHash()) && left.baseCommit().equals(right.baseCommit()) && left.indexTree().equals(right.indexTree()) && left.diffFingerprint().equals(right.diffFingerprint())
                && left.files().stream().map(file -> StoredPassportIdentity.pathHash(file.path())).sorted().toList().equals(right.files().stream().map(file -> StoredPassportIdentity.pathHash(file.path())).sorted().toList());
    }
}
