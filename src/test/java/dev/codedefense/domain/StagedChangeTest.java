package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StagedChangeTest {
    private static final String SHA_256 = "a".repeat(64);
    private static final String GIT_ID = "b".repeat(40);
    private static final String INDEX_ID = "c".repeat(64);

    @Test
    void constructsAnImmutableOrderedStagedChange() {
        List<StagedChangeFile> files = new ArrayList<>(List.of(file("src/App.java")));

        StagedChange change = new StagedChange(Path.of("C:/repository"), SHA_256, GIT_ID, INDEX_ID,
                SHA_256, files, 3, 1);
        files.clear();

        assertEquals(Path.of("C:/repository").toAbsolutePath().normalize(), change.repositoryRoot());
        assertEquals(1, change.files().size());
        assertThrows(UnsupportedOperationException.class, () -> change.files().add(file("src/Other.java")));
    }

    @Test
    void rejectsInvalidRootHashesGitIdsAndLineTotals() {
        assertThrows(IllegalArgumentException.class, () -> new StagedChange(Path.of("relative"), SHA_256, GIT_ID,
                INDEX_ID, SHA_256, List.of(file("src/App.java")), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> changeWithIdentity("A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> changeWithDiff("a".repeat(63)));
        assertThrows(IllegalArgumentException.class, () -> changeWithBase("a".repeat(39)));
        assertThrows(IllegalArgumentException.class, () -> changeWithIndex("A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new StagedChange(root(), SHA_256, GIT_ID, INDEX_ID, SHA_256,
                List.of(file("src/App.java")), -1, 0));
    }

    @Test
    void rejectsUnsafeFilePathsAndLineCounts() {
        assertThrows(IllegalArgumentException.class, () -> new StagedChangeFile(Path.of("C:/absolute"), StagedFileStatus.ADDED, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StagedChangeFile(Path.of("src/../App.java"), StagedFileStatus.ADDED, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StagedChangeFile(Path.of("src", "bad\u0000name"), StagedFileStatus.ADDED, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new StagedChangeFile(Path.of("src/App.java"), StagedFileStatus.ADDED, -1, 0));
    }

    @Test
    void requiresAUniqueSafePreviousPathOnlyForRenames() {
        StagedChangeFile renamed = new StagedChangeFile(Path.of("src/New.java"),
                Optional.of(Path.of("src/Old.java")), StagedFileStatus.RENAMED, 0, 0);

        assertEquals(Path.of("src/Old.java"), renamed.previousPath().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new StagedChangeFile(Path.of("src/New.java"),
                Optional.empty(), StagedFileStatus.RENAMED, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StagedChangeFile(Path.of("src/App.java"),
                Optional.of(Path.of("src/Old.java")), StagedFileStatus.MODIFIED, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StagedChangeFile(Path.of("src/New.java"),
                Optional.of(Path.of("src/New.java")), StagedFileStatus.RENAMED, 0, 0));
    }

    @Test
    void rejectsDuplicateOrUnorderedFiles() {
        assertThrows(IllegalArgumentException.class, () -> new StagedChange(root(), SHA_256, GIT_ID, INDEX_ID, SHA_256,
                List.of(file("src/B.java"), file("src/A.java")), 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new StagedChange(root(), SHA_256, GIT_ID, INDEX_ID, SHA_256,
                List.of(file("src/A.java"), file("src/A.java")), 2, 0));
    }

    @Test
    void doesNotExposeFutureSourceContentInToString() {
        StagedChange change = new StagedChange(root(), SHA_256, GIT_ID, INDEX_ID, SHA_256,
                List.of(file("src/App.java")), 1, 0);

        assertDoesNotThrow(change::toString);
        assertEquals(false, change.toString().contains("future-source-content"));
    }

    private static StagedChange changeWithIdentity(String identity) {
        return new StagedChange(root(), identity, GIT_ID, INDEX_ID, SHA_256, List.of(file("src/App.java")), 0, 0);
    }

    private static StagedChange changeWithDiff(String fingerprint) {
        return new StagedChange(root(), SHA_256, GIT_ID, INDEX_ID, fingerprint, List.of(file("src/App.java")), 0, 0);
    }

    private static StagedChange changeWithBase(String base) {
        return new StagedChange(root(), SHA_256, base, INDEX_ID, SHA_256, List.of(file("src/App.java")), 0, 0);
    }

    private static StagedChange changeWithIndex(String index) {
        return new StagedChange(root(), SHA_256, GIT_ID, index, SHA_256, List.of(file("src/App.java")), 0, 0);
    }

    private static Path root() {
        return Path.of("C:/repository").toAbsolutePath().normalize();
    }

    private static StagedChangeFile file(String path) {
        return new StagedChangeFile(Path.of(path), StagedFileStatus.ADDED, 1, 0);
    }
}
