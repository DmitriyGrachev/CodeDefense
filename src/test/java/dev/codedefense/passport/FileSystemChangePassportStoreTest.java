package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileSystemChangePassportStoreTest {
    @TempDir Path directory;
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
    void collisionNamingAndAtomicMoveFallbackAreSafe() {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        Clock fixed = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths, new MarkdownChangePassportRenderer(), fixed,
                (source, target, options) -> {
                    if (options.length > 0 && attempts.getAndIncrement() == 1) throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test");
                    attempts.incrementAndGet();
                    Files.move(source, target, options);
                });
        Path first = store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT));
        Path second = store.save(PassportTestFixtures.passport(dev.codedefense.domain.PassportStatus.CURRENT));
        assertTrue(attempts.get() >= 3);
        assertFalse(first.equals(second));
        assertTrue(second.getFileName().toString().contains("-2.md"));
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
