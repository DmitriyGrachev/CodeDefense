package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.StagedChangeIdentity;
import dev.codedefense.passport.PassportTestFixtures;
import dev.codedefense.passport.StoredPassportIdentity;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VerifyLatestChangePassportUseCaseTest {
    @Test
    void returnsCurrentForMatchingCapturedIdentity() {
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        StagedChangeSource source = capturingSource(passport.change());
        StoredPassportIdentity identity = StoredPassportIdentity.from(passport, Path.of("passport.md").toAbsolutePath());
        dev.codedefense.passport.ChangePassportStore store = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(dev.codedefense.domain.ChangePassport ignored) { throw new UnsupportedOperationException(); }
            public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.of(identity); }
        };
        assertEquals(PassportStatus.CURRENT, new VerifyLatestChangePassportUseCase(source, store).verify(passport.change().repositoryRoot()).orElseThrow().status());
    }

    @Test
    void returnsExpiredForDifferentIndexWithoutRewritingArtifact() {
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        var changed = new dev.codedefense.domain.StagedChange(passport.change().repositoryRoot(), passport.change().repositoryIdentityHash(), passport.change().baseCommit(), passport.change().indexIdentity(), "e".repeat(64), passport.change().files(), 2, 1);
        StagedChangeSource source = capturingSource(changed);
        StoredPassportIdentity identity = StoredPassportIdentity.from(passport, Path.of("passport.md").toAbsolutePath());
        dev.codedefense.passport.ChangePassportStore store = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(dev.codedefense.domain.ChangePassport ignored) { throw new AssertionError("verification must not save"); }
            public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.of(identity); }
        };
        assertEquals(PassportStatus.EXPIRED, new VerifyLatestChangePassportUseCase(source, store).verify(passport.change().repositoryRoot()).orElseThrow().status());
    }

    @Test
    void returnsExpiredForDifferentRepositoryAndEmptyForNoLatestArtifact() {
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        var otherRoot = Path.of("C:/other/project").toAbsolutePath().normalize();
        var differentRepository = new dev.codedefense.domain.StagedChange(otherRoot, "f".repeat(64), passport.change().baseCommit(), passport.change().indexIdentity(), passport.change().diffFingerprint(), passport.change().files(), 2, 1);
        StagedChangeSource source = capturingSource(differentRepository);
        StoredPassportIdentity identity = StoredPassportIdentity.from(passport, Path.of("passport.md").toAbsolutePath());
        dev.codedefense.passport.ChangePassportStore stored = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(dev.codedefense.domain.ChangePassport ignored) { throw new AssertionError("verification must not save"); }
            public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.of(identity); }
        };
        assertEquals(PassportStatus.EXPIRED, new VerifyLatestChangePassportUseCase(source, stored).verify(otherRoot).orElseThrow().status());
        dev.codedefense.passport.ChangePassportStore absent = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(dev.codedefense.domain.ChangePassport ignored) { throw new AssertionError("verification must not save"); }
            public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.empty(); }
        };
        assertTrue(new VerifyLatestChangePassportUseCase(source, absent).verify(otherRoot).isEmpty());
    }

    @Test
    void returnsExpiredWhenTheStoredPassportHasAnEmptyCurrentIndex() {
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        StagedChangeIdentity empty = new StagedChangeIdentity(
                passport.change().repositoryRoot(), passport.change().repositoryIdentityHash(), passport.change().baseCommit(),
                passport.change().indexIdentity(), passport.change().diffFingerprint(), java.util.List.of());
        StoredPassportIdentity identity = StoredPassportIdentity.from(passport, Path.of("passport.md").toAbsolutePath());
        dev.codedefense.passport.ChangePassportStore store = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(dev.codedefense.domain.ChangePassport ignored) { throw new AssertionError("verification must not save"); }
            public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.of(identity); }
        };

        assertEquals(PassportStatus.EXPIRED,
                new VerifyLatestChangePassportUseCase(identitySource(empty), store)
                        .verify(passport.change().repositoryRoot()).orElseThrow().status());
    }

    @Test
    void returnsInformationalAbsenceWithoutCapturingGitState() {
        StagedChangeSource source = new StagedChangeSource() {
            @Override public CapturedStagedChange capture(Path ignored) { throw new AssertionError("must not capture"); }
            @Override public StagedChangeIdentity captureIdentity(Path ignored) { throw new AssertionError("must not capture"); }
            @Override public dev.codedefense.domain.StagedChange inspect(Path ignored) {
                throw new AssertionError("must not inspect");
            }
        };
        dev.codedefense.passport.ChangePassportStore absent = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(dev.codedefense.domain.ChangePassport ignored) { throw new AssertionError("verification must not save"); }
            public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.empty(); }
        };

        assertTrue(new VerifyLatestChangePassportUseCase(source, absent).verify(Path.of(".")).isEmpty());
    }

    private static StagedChangeSource identitySource(StagedChangeIdentity identity) {
        return new StagedChangeSource() {
            @Override public CapturedStagedChange capture(Path ignored) { throw new AssertionError("must not do initial capture"); }
            @Override public StagedChangeIdentity captureIdentity(Path ignored) { return identity; }
            @Override public dev.codedefense.domain.StagedChange inspect(Path ignored) {
                throw new AssertionError("must not inspect");
            }
        };
    }

    private static StagedChangeSource capturingSource(dev.codedefense.domain.StagedChange change) {
        return new StagedChangeSource() {
            @Override public CapturedStagedChange capture(Path ignored) {
                return new CapturedStagedChange(change, java.util.List.of());
            }
            @Override public dev.codedefense.domain.StagedChange inspect(Path ignored) { return change; }
        };
    }
}
