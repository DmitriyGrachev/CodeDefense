package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.passport.PassportTestFixtures;
import dev.codedefense.passport.StoredPassportIdentity;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VerifyLatestChangePassportUseCaseTest {
    @Test
    void returnsCurrentForMatchingCapturedIdentity() {
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        StagedChangeSource source = ignored -> new CapturedStagedChange(passport.change(), java.util.List.of(), "");
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
        var changed = new dev.codedefense.domain.StagedChange(passport.change().repositoryRoot(), passport.change().repositoryIdentityHash(), passport.change().baseCommit(), passport.change().indexTree(), "e".repeat(64), passport.change().files(), 2, 1);
        StagedChangeSource source = ignored -> new CapturedStagedChange(changed, java.util.List.of(), "");
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
        var differentRepository = new dev.codedefense.domain.StagedChange(otherRoot, "f".repeat(64), passport.change().baseCommit(), passport.change().indexTree(), passport.change().diffFingerprint(), passport.change().files(), 2, 1);
        StagedChangeSource source = ignored -> new CapturedStagedChange(differentRepository, java.util.List.of(), "");
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
}
