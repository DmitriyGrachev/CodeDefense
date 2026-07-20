package dev.codedefense.passport;

import dev.codedefense.domain.PassportStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileSystemChangePassportStoreTest {
    @TempDir Path directory;

    @Test
    void savesPairedMarkdownAndJsonAndReadsLatestReceipt() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), new PassportReceiptJsonCodec(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                () -> "7bd53719-1de8-4c78-a48a-430aa38555dc");

        Path markdown = store.save(PassportTestFixtures.passport(
                dev.codedefense.domain.PassportStatus.CURRENT));
        StoredChangePassport latest = store.readLatest().orElseThrow();

        assertEquals(markdown, latest.markdownPath());
        assertEquals("19700101-000000-000-change-passport.json",
                latest.receiptPath().getFileName().toString());
        assertTrue(Files.isRegularFile(latest.receiptPath()));
        assertEquals("7bd53719-1de8-4c78-a48a-430aa38555dc", latest.receipt().receiptId());
    }

    @Test
    void listsPairedPassportsNewestFirstAndRejectsCorruptSidecar() throws Exception {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.util.concurrent.atomic.AtomicReference<Instant> now =
                new java.util.concurrent.atomic.AtomicReference<>(Instant.EPOCH);
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), new PassportReceiptJsonCodec(), clock,
                () -> java.util.UUID.randomUUID().toString());
        store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT));
        now.set(Instant.ofEpochMilli(1));
        store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.EXPIRED));

        assertEquals(2, store.list(50).size());
        assertEquals(dev.codedefense.domain.PassportStatus.EXPIRED,
                store.list(50).getFirst().receipt().statusAtCreation());
        Files.writeString(store.list(50).getFirst().receiptPath(), "{bad json}");
        assertThrows(ChangePassportPersistenceException.class, store::readLatest);
    }
    @Test
    void savesAndReadsOnlyIdentityMetadata() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        Path saved = store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT));
        StoredPassportIdentity identity = store.readLatestIdentity().orElseThrow();
        assertTrue(java.nio.file.Files.exists(saved));
        assertEquals("a".repeat(64), identity.repositoryIdentityHash());
        assertEquals(saved, identity.passport());
    }

    @Test
    void readOnlyAbsenceDoesNotCreateStorage() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(), Clock.systemUTC());
        assertTrue(store.readLatestIdentity().isEmpty());
        assertFalse(java.nio.file.Files.exists(paths.rootDirectory()));
    }

    @Test
    void rejectsMalformedLatestPointer() throws Exception {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.nio.file.Files.createDirectory(paths.rootDirectory());
        java.nio.file.Files.createDirectory(paths.passportsDirectory());
        java.nio.file.Files.writeString(paths.latestPointer(), "../escape.md\n");
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(), Clock.systemUTC());
        assertThrows(ChangePassportPersistenceException.class, store::readLatestIdentity);
    }

    @Test
    void rejectsInvalidUtf8AndOversizedPointer() throws Exception {
        ChangePassportPaths paths = preparedPaths();
        Files.write(paths.latestPointer(), new byte[] {(byte) 0xc3, (byte) 0x28});
        FileSystemChangePassportStore store = store(paths);
        assertThrows(ChangePassportPersistenceException.class, store::readLatestIdentity);
        Files.writeString(paths.latestPointer(), "x".repeat(4097));
        assertThrows(ChangePassportPersistenceException.class, store::readLatestIdentity);
    }

    @Test
    void rejectsInvalidUtf8OversizedAndMalformedArtifactMetadata() throws Exception {
        ChangePassportPaths paths = preparedPaths();
        Path artifact = paths.passportsDirectory().resolve("passport.md");
        Files.write(artifact, new byte[] {(byte) 0xc3, (byte) 0x28});
        Files.writeString(paths.latestPointer(), artifact.toAbsolutePath() + "\n");
        FileSystemChangePassportStore store = store(paths);
        assertThrows(ChangePassportPersistenceException.class, store::readLatestIdentity);
        Files.writeString(artifact, "x".repeat(1024 * 1024 + 1));
        assertThrows(ChangePassportPersistenceException.class, store::readLatestIdentity);
        Files.writeString(artifact, "# Passport\n<!-- codedefense-change-passport:v1;root=" + "a".repeat(64) + " -->\n");
        assertThrows(ChangePassportPersistenceException.class, store::readLatestIdentity);
    }

    @Test
    void readsLegacyTreeMetadataAsAnExpiredOnlyIdentity() throws Exception {
        ChangePassportPaths paths = preparedPaths();
        Path artifact = paths.passportsDirectory().resolve("legacy.md");
        String markdown = "<!-- codedefense-change-passport:v1;root=" + "a".repeat(64)
                + ";base=" + "b".repeat(40) + ";tree=" + "c".repeat(40) + ";diff=" + "d".repeat(64)
                + ";paths=" + StoredPassportIdentity.pathHash(Path.of("src/App.java"))
                + ";timestamp=2026-07-18T00:00:00Z -->\n";
        Files.writeString(artifact, markdown);
        Files.writeString(paths.latestPointer(), artifact.toAbsolutePath() + "\n");

        assertTrue(store(paths).readLatestIdentity().isPresent());
    }

    @Test
    void rejectsFinalAndIntermediateSymlinksWhenPlatformSupportsThem() throws Exception {
        ChangePassportPaths paths = preparedPaths();
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Path external = outside.resolve("passport.md");
        Files.writeString(external, "outside");
        Path finalLink = paths.passportsDirectory().resolve("final.md");
        assumeSymlink(finalLink, external);
        Files.writeString(paths.latestPointer(), finalLink.toAbsolutePath() + "\n");
        assertThrows(ChangePassportPersistenceException.class, store(paths)::readLatestIdentity);

        Files.deleteIfExists(paths.latestPointer());
        Path linkDirectory = paths.passportsDirectory().resolve("link");
        assumeSymlink(linkDirectory, outside);
        Files.writeString(paths.latestPointer(), linkDirectory.resolve("passport.md").toAbsolutePath() + "\n");
        assertThrows(ChangePassportPersistenceException.class, store(paths)::readLatestIdentity);
    }

    @Test
    void artifactMoveAttemptsAtomicMove() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.util.concurrent.atomic.AtomicReference<java.nio.file.CopyOption[]> artifactOptions = new java.util.concurrent.atomic.AtomicReference<>();
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                (source, target, options) -> {
                    if (target.getFileName().toString().endsWith(".md")) artifactOptions.set(options.clone());
                    Files.move(source, target, options);
                });
        store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT));
        assertTrue(java.util.Arrays.asList(artifactOptions.get()).contains(StandardCopyOption.ATOMIC_MOVE));
    }

    @Test
    void artifactMoveFallsBackWhenAtomicMoveIsUnsupported() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.util.concurrent.atomic.AtomicBoolean rejectedAtomicArtifactMove = new java.util.concurrent.atomic.AtomicBoolean();
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), (source, target, options) -> {
                    if (target.getFileName().toString().endsWith(".md")
                            && java.util.Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)
                            && rejectedAtomicArtifactMove.compareAndSet(false, true)) {
                        throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test");
                    }
                    Files.move(source, target, options);
                });

        Path saved = store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT));

        assertTrue(rejectedAtomicArtifactMove.get());
        assertTrue(Files.isRegularFile(saved));
    }

    @Test
    void racedArtifactCollisionRemainsOwnedByTheOtherWriter() throws Exception {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        Path colliding = paths.passportsDirectory().resolve("19700101-000000-000-change-passport.md");
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), (source, target, options) -> {
                    if (target.equals(colliding)) {
                        Files.writeString(target, "other writer artifact");
                        throw new java.io.IOException("collision");
                    }
                    Files.move(source, target, options);
                });

        assertThrows(ChangePassportPersistenceException.class,
                () -> store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT)));
        assertEquals("other writer artifact", Files.readString(colliding));
    }

    @Test
    void saveNeverDeletesAnotherWritersLiveTemporaryFiles() throws Exception {
        ChangePassportPaths paths = preparedPaths();
        Path foreignArtifactTemp = paths.passportsDirectory().resolve(".passport-foreign.tmp");
        Path foreignPointerTemp = paths.rootDirectory().resolve(".latest-passport-foreign.tmp");
        Files.writeString(foreignArtifactTemp, "writer A artifact");
        Files.writeString(foreignPointerTemp, "writer A pointer");

        store(paths).save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT));

        assertEquals("writer A artifact", Files.readString(foreignArtifactTemp));
        assertEquals("writer A pointer", Files.readString(foreignPointerTemp));
    }

    @Test
    void failedPointerPublicationCleansSavedArtifactAndTemps() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.util.concurrent.atomic.AtomicInteger moves = new java.util.concurrent.atomic.AtomicInteger();
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(), Clock.systemUTC(),
                (source, target, options) -> {
                    if (moves.incrementAndGet() >= 2) throw new java.io.IOException("forced failure");
                    Files.move(source, target, options);
                });
        assertThrows(ChangePassportPersistenceException.class, () -> store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT)));
        assertTrue(Files.exists(paths.passportsDirectory()));
        try (var files = Files.list(paths.passportsDirectory())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".md") || path.getFileName().toString().endsWith(".tmp")));
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    @Test
    void appendsAttemptLineageWithoutRewritingPreviousArtifacts() throws Exception {
        Path home = Files.createDirectory(directory.resolve("lineage-home"));
        ChangePassportPaths paths = ChangePassportPaths.under(home);
        java.util.ArrayDeque<String> ids = new java.util.ArrayDeque<>(List.of(
                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"));
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), new PassportReceiptJsonCodec(),
                java.time.Clock.fixed(java.time.Instant.parse("2026-07-19T12:00:00Z"), java.time.ZoneOffset.UTC),
                ids::removeFirst);
        Path first = store.save(PassportTestFixtures.passport(PassportStatus.CURRENT));
        byte[] firstMarkdown = Files.readAllBytes(first);
        Path sidecar = first.resolveSibling(first.getFileName().toString().replace(".md", ".json"));
        byte[] firstReceipt = Files.readAllBytes(sidecar);

        store.save(PassportTestFixtures.passport(PassportStatus.CURRENT));

        List<StoredChangePassport> history = store.listByFingerprint("d".repeat(64), 20);
        assertEquals(2, history.size());
        assertEquals(2, history.getFirst().receipt().attemptNumber());
        assertEquals(history.getLast().receipt().attemptId(), history.getFirst().receipt().supersedes().orElseThrow());
        assertArrayEquals(firstMarkdown, Files.readAllBytes(first));
        assertArrayEquals(firstReceipt, Files.readAllBytes(sidecar));
    }

    @Test
    void repositoryQueryFiltersBeforeApplyingTheNewestReceiptLimit() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), new PassportReceiptJsonCodec(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                () -> "00000000-0000-4000-8000-%012d".formatted(ids.incrementAndGet()));
        store.save(passportWithRepository("a".repeat(64)));
        for (int index = 0; index < 51; index++) {
            store.save(passportWithRepository("e".repeat(64)));
        }

        List<StoredChangePassport> result = store.listByRepository("a".repeat(64), 50);

        assertEquals(1, result.size());
        assertEquals("a".repeat(64), result.getFirst().receipt().repositoryIdentityHash());
    }

    @Test
    void repositoryQueryReturnsItsNewestTwentyEvenAfterMoreThanFiftyOtherReceipts() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), new PassportReceiptJsonCodec(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                () -> "20000000-0000-4000-8000-%012d".formatted(ids.incrementAndGet()));
        for (int index = 0; index < 21; index++) {
            store.save(passportWithRepository("a".repeat(64)));
        }
        for (int index = 0; index < 51; index++) {
            store.save(passportWithRepository("e".repeat(64)));
        }

        List<StoredChangePassport> result = store.listByRepository("a".repeat(64), 20);

        assertEquals(20, result.size());
        assertTrue(result.stream().allMatch(value ->
                value.receipt().repositoryIdentityHash().equals("a".repeat(64))));
        assertEquals(21, result.getFirst().receipt().attemptNumber());
        assertEquals(2, result.getLast().receipt().attemptNumber());
    }

    @Test
    void repositoryAndKindQueryFiltersSameRepositoryCommitsBeforeApplyingTheLimit() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), new PassportReceiptJsonCodec(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                () -> "10000000-0000-4000-8000-%012d".formatted(ids.incrementAndGet()));
        store.save(passportWithIdentity("a".repeat(64), dev.codedefense.domain.ChangeKind.STAGED,
                "c".repeat(64)));
        for (int index = 0; index < 51; index++) {
            store.save(passportWithIdentity("a".repeat(64), dev.codedefense.domain.ChangeKind.COMMIT,
                    "f".repeat(40)));
        }

        List<StoredChangePassport> result = store.listByRepositoryAndKind(
                "a".repeat(64), dev.codedefense.domain.ChangeKind.STAGED, 50);

        assertEquals(1, result.size());
        assertEquals(dev.codedefense.domain.ChangeKind.STAGED, result.getFirst().receipt().changeKind());
    }

    @Test
    void repositoryQueryValidatesHashAndLimit() {
        FileSystemChangePassportStore store = store(ChangePassportPaths.under(directory));
        assertThrows(IllegalArgumentException.class, () -> store.listByRepository("not-a-hash", 1));
        assertThrows(IllegalArgumentException.class, () -> store.listByRepository("a".repeat(64), 0));
        assertThrows(IllegalArgumentException.class, () -> store.listByRepository("a".repeat(64), 51));
        assertThrows(IllegalArgumentException.class, () -> store.listByRepositoryAndKind(
                "not-a-hash", dev.codedefense.domain.ChangeKind.STAGED, 1));
        assertThrows(NullPointerException.class, () -> store.listByRepositoryAndKind(
                "a".repeat(64), null, 1));
        assertThrows(IllegalArgumentException.class, () -> store.listByRepositoryAndKind(
                "a".repeat(64), dev.codedefense.domain.ChangeKind.STAGED, 0));
        assertThrows(IllegalArgumentException.class, () -> store.listByRepositoryAndKind(
                "a".repeat(64), dev.codedefense.domain.ChangeKind.STAGED, 51));
    }

    private static dev.codedefense.domain.ChangePassport passportWithRepository(String repositoryHash) {
        dev.codedefense.domain.ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        return passportWithIdentity(repositoryHash, original.changeKind(), original.sourceIdentity());
    }

    private static dev.codedefense.domain.ChangePassport passportWithIdentity(String repositoryHash,
            dev.codedefense.domain.ChangeKind kind, String sourceIdentity) {
        dev.codedefense.domain.ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        dev.codedefense.domain.StagedChange oldChange = original.change();
        dev.codedefense.domain.StagedChange change = new dev.codedefense.domain.StagedChange(
                oldChange.repositoryRoot(), repositoryHash, oldChange.baseCommit(), oldChange.indexIdentity(),
                oldChange.diffFingerprint(), oldChange.files(), oldChange.addedLines(), oldChange.deletedLines());
        return new dev.codedefense.domain.ChangePassport(change, kind, sourceIdentity,
                original.analysis(), original.session(), original.createdAt(), original.model(),
                original.statusAtCreation(), original.focus(), original.codexProvenance());
    }

    private ChangePassportPaths preparedPaths() throws Exception {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        Files.createDirectory(paths.rootDirectory());
        Files.createDirectory(paths.passportsDirectory());
        return paths;
    }

    private static FileSystemChangePassportStore store(ChangePassportPaths paths) {
        return new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(), Clock.systemUTC());
    }

    private static void assumeSymlink(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            assumeTrue(false, "Symbolic links are unavailable on this platform");
        }
    }
}
