package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.StagedChangeIdentity;
import dev.codedefense.passport.PassportTestFixtures;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChangePassportServiceTest {
    @Test
    void savesCurrentWhenRecapturedIdentityMatches() {
        ChangePassport before = PassportTestFixtures.passport(PassportStatus.CURRENT);
        StagedChangeSource source = ignored -> new CapturedStagedChange(before.change(), java.util.List.of());
        AtomicReference<ChangePassport> saved = new AtomicReference<>();
        dev.codedefense.passport.ChangePassportStore store = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(ChangePassport passport) { saved.set(passport); return Path.of("passport.md"); }
            public java.util.Optional<dev.codedefense.passport.StoredPassportIdentity> readLatestIdentity() { return java.util.Optional.empty(); }
        };
        new ChangePassportService(source, store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "model")
                .createAndSave(before.change(), before.analysis(), before.session());
        assertEquals(PassportStatus.CURRENT, saved.get().statusAtCreation());
    }

    @Test
    void savesExpiredWhenIndexChangesBeforeSave() {
        ChangePassport before = PassportTestFixtures.passport(PassportStatus.CURRENT);
        dev.codedefense.domain.StagedChange changed = new dev.codedefense.domain.StagedChange(before.change().repositoryRoot(), before.change().repositoryIdentityHash(), before.change().baseCommit(), before.change().indexTree(), "e".repeat(64), before.change().files(), before.change().addedLines(), before.change().deletedLines());
        StagedChangeSource source = ignored -> new CapturedStagedChange(changed, java.util.List.of());
        AtomicReference<ChangePassport> saved = new AtomicReference<>();
        dev.codedefense.passport.ChangePassportStore store = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(ChangePassport passport) { saved.set(passport); return Path.of("passport.md"); }
            public java.util.Optional<dev.codedefense.passport.StoredPassportIdentity> readLatestIdentity() { return java.util.Optional.empty(); }
        };
        ChangePassportService service = new ChangePassportService(source, store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "model");
        service.createAndSave(before.change(), before.analysis(), before.session());
        assertEquals(PassportStatus.EXPIRED, saved.get().statusAtCreation());
    }

    @Test
    void savesExpiredWhenTheIndexIsClearedDuringTheInterview() {
        ChangePassport before = PassportTestFixtures.passport(PassportStatus.CURRENT);
        StagedChangeSource source = identitySource(new StagedChangeIdentity(
                before.change().repositoryRoot(), before.change().repositoryIdentityHash(), before.change().baseCommit(),
                before.change().indexTree(), before.change().diffFingerprint(), java.util.List.of()));
        AtomicReference<ChangePassport> saved = new AtomicReference<>();
        dev.codedefense.passport.ChangePassportStore store = new dev.codedefense.passport.ChangePassportStore() {
            public Path save(ChangePassport passport) { saved.set(passport); return Path.of("passport.md"); }
            public java.util.Optional<dev.codedefense.passport.StoredPassportIdentity> readLatestIdentity() { return java.util.Optional.empty(); }
        };

        new ChangePassportService(source, store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "model")
                .createAndSave(before.change(), before.analysis(), before.session());

        assertEquals(PassportStatus.EXPIRED, saved.get().statusAtCreation());
    }

    private static StagedChangeSource identitySource(StagedChangeIdentity identity) {
        return new StagedChangeSource() {
            @Override public CapturedStagedChange capture(Path ignored) { throw new AssertionError("initial capture is not a recapture"); }
            @Override public StagedChangeIdentity captureIdentity(Path ignored) { return identity; }
        };
    }
}
