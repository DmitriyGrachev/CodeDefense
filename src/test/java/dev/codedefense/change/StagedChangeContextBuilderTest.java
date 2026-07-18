package dev.codedefense.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.EmptyProjectSnapshotException;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StagedChangeContextBuilderTest {
    private static final Path ROOT = Path.of("C:/workspace/demo").toAbsolutePath().normalize();

    @Test
    void buildsBoundedRedactedContextFromIndexBlobsOnly() {
        CapturedStagedChange captured = captured(List.of(
                blob("src/Main.java", StagedFileStatus.MODIFIED,
                        "password=TOPSECRET\npublic class Main { String text = \"staged\"; }\n",
                        "public class Main { String text = \"base\"; }\n"),
                blob("README.md", StagedFileStatus.ADDED, "# Staged README\n", null),
                blob("src/Deleted.java", StagedFileStatus.DELETED, null, "class Deleted {}\n")),
                "diff --git a/src/Main.java b/src/Main.java\n+index 111..222 100644\n");

        ProjectSnapshot snapshot = new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 1_024))
                .build(captured);

        assertTrue(snapshot.promptContent().contains("INDEX_FILE: src/Main.java"));
        assertTrue(snapshot.promptContent().contains("HEAD_FILE: src/Main.java"));
        assertTrue(snapshot.promptContent().contains("STATUS: MODIFIED"));
        assertTrue(snapshot.promptContent().contains("1 | password=[REDACTED]"));
        assertFalse(snapshot.promptContent().contains("TOPSECRET"));
        assertFalse(snapshot.promptContent().contains("UNSTAGED_WORKTREE_SENTINEL"));
        assertEquals(1, snapshot.redactionCount());
        assertEquals(List.of(Path.of("README.md"), Path.of("src/Main.java")),
                snapshot.selectedFiles().stream().map(ProjectSnapshot.SelectedFile::relativePath).toList());
        assertFalse(snapshot.selectedFiles().stream().map(ProjectSnapshot.SelectedFile::relativePath)
                .anyMatch(path -> path.toString().contains("Deleted")));
    }

    @Test
    void appliesDeterministicLimitsUnicodeLineNumbersAndFinalSeparators() {
        List<IndexBlob> blobs = new ArrayList<>();
        for (int index = 0; index < 31; index++) {
            blobs.add(blob("src/File%02d.java".formatted(index), StagedFileStatus.ADDED,
                    "class File%02d { String value = \"кириллица 😀 ".formatted(index)
                            + "x".repeat(200) + "\"; }\n", null));
        }
        CodeDefenseConfig config = new CodeDefenseConfig(30, 10_000, 180);
        ProjectSnapshot snapshot = new StagedChangeContextBuilder(config).build(captured(blobs, "diff\n"));

        assertEquals(30, snapshot.selectedFiles().size());
        assertEquals(snapshot.selectedFiles().stream().map(ProjectSnapshot.SelectedFile::relativePath).sorted().toList(),
                snapshot.selectedFiles().stream().map(ProjectSnapshot.SelectedFile::relativePath).toList());
        assertTrue(snapshot.selectedFiles().stream().allMatch(ProjectSnapshot.SelectedFile::truncated));
        assertTrue(snapshot.selectedFiles().stream().allMatch(file -> file.renderedBytes() <= config.maximumFileBlockBytes()));
        assertTrue(snapshot.promptBytes() <= config.maximumSnapshotBytes());
        assertFalse(snapshot.promptContent().contains("\uFFFD"));
        assertTrue(snapshot.promptContent().contains("1 | class File00"));
    }

    @Test
    void rejectsChangeWithNoEligibleCurrentStagedText() {
        CapturedStagedChange captured = captured(List.of(
                blob("src/Deleted.java", StagedFileStatus.DELETED, null, "class Deleted {}\n"),
                blob("private.key", StagedFileStatus.ADDED, "secret", null)), "diff\n");

        assertThrows(EmptyProjectSnapshotException.class,
                () -> new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 1_024)).build(captured));
    }

    @Test
    void rendersOnlySafeStagedChangePreviewMetadata() {
        CapturedStagedChange captured = captured(List.of(
                blob("src/Main.java", StagedFileStatus.MODIFIED, "class Main {}\n", "class Base {}\n"),
                blob("src/Old.java", StagedFileStatus.DELETED, null, "class Old {}\n")),
                "private staged diff");
        ProjectSnapshot snapshot = new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 1_024))
                .build(captured);
        StringWriter text = new StringWriter();

        new StagedChangePreviewRenderer(new CodeDefenseConfig(30, 4_096, 1_024))
                .render(captured.change(), snapshot, new PrintWriter(text));

        assertTrue(text.toString().contains("Mode: Staged change"));
        assertTrue(text.toString().contains("Unstaged working-tree content ignored: yes"));
        assertTrue(text.toString().contains("Selected-file limit: 30"));
        assertTrue(text.toString().contains("src/Main.java"));
        assertFalse(text.toString().contains("private staged diff"));
        assertFalse(text.toString().contains("class Main"));
        assertFalse(text.toString().contains(ROOT.toString()));
    }

    @Test
    void usesMetadataOnlyCanonicalDiffPrefix() {
        CapturedStagedChange captured = captured(List.of(
                blob("src/Main.java", StagedFileStatus.ADDED, "class Main {}\n", null)),
                "x".repeat(200) + "\n+password=TOPSECRET\n" + "y".repeat(2_000));

        ProjectSnapshot snapshot = new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 1_024))
                .build(captured);

        assertTrue(snapshot.promptContent().contains("diff --staged src/Main.java"));
        assertFalse(snapshot.promptContent().contains("TOPSECRET"));
        assertFalse(snapshot.promptContent().contains("[REDACTED]"));
        assertEquals(0, snapshot.redactionCount());
    }

    @Test
    void reservesTheDiffPrefixFinalNewlineAtTheTightByteBoundary() {
        StagedChangeContextBuilder builder = new StagedChangeContextBuilder(new CodeDefenseConfig(1, 10, 1));
        StringBuilder prompt = new StringBuilder("x".repeat(9));

        builder.appendDiffPrefix(prompt, "secret");

        assertEquals(10, prompt.toString().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(prompt.toString().endsWith("\n"));
    }

    @Test
    void countsOnlyRedactionMarkersThatFitInsideAnIncludedFileBlock() {
        CapturedStagedChange captured = captured(List.of(
                blob("src/Main.java", StagedFileStatus.ADDED,
                        "x".repeat(500) + "\npassword=TOPSECRET\n", null)), "diff\n");

        ProjectSnapshot snapshot = new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 180))
                .build(captured);

        assertTrue(snapshot.selectedFiles().getFirst().truncated());
        assertFalse(snapshot.promptContent().contains("TOPSECRET"));
        assertFalse(snapshot.promptContent().contains("[REDACTED]"));
        assertEquals(0, snapshot.redactionCount());
    }

    @Test
    void excludesSupportedFilesBelowExcludedDirectories() {
        CapturedStagedChange captured = captured(List.of(
                blob("src/Main.java", StagedFileStatus.ADDED, "class Main {}\n", null),
                blob("node_modules/Dependency.java", StagedFileStatus.ADDED,
                        "UNTRUSTED_DEPENDENCY_SENTINEL\n", null)), "diff\n");

        ProjectSnapshot snapshot = new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 1_024))
                .build(captured);

        assertEquals(List.of(Path.of("src/Main.java")),
                snapshot.selectedFiles().stream().map(ProjectSnapshot.SelectedFile::relativePath).toList());
        assertFalse(snapshot.promptContent().contains("UNTRUSTED_DEPENDENCY_SENTINEL"));
    }

    @Test
    void excludesIneligibleCanonicalDiffContentFromThePrompt() {
        CapturedStagedChange captured = captured(List.of(
                blob("src/Main.java", StagedFileStatus.ADDED, "class Main {}\n", null),
                blob("private.key", StagedFileStatus.ADDED, "-----BEGIN PRIVATE KEY-----\nKEY_SECRET_SENTINEL\n", null),
                blob("node_modules/Dependency.java", StagedFileStatus.ADDED,
                        "NODE_MODULES_SECRET_SENTINEL\n", null)),
                "diff --git a/private.key b/private.key\n+-----BEGIN PRIVATE KEY-----\nKEY_SECRET_SENTINEL\n"
                        + "diff --git a/node_modules/Dependency.java b/node_modules/Dependency.java\n"
                        + "+NODE_MODULES_SECRET_SENTINEL\n");

        ProjectSnapshot snapshot = new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 1_024))
                .build(captured);

        assertFalse(snapshot.promptContent().contains("BEGIN PRIVATE KEY"));
        assertFalse(snapshot.promptContent().contains("KEY_SECRET_SENTINEL"));
        assertFalse(snapshot.promptContent().contains("NODE_MODULES_SECRET_SENTINEL"));
        assertTrue(snapshot.promptContent().contains("CANONICAL_DIFF:"));
    }

    @Test
    void ignoresUnlistedAndDeletedBlobContentButKeepsDeclaredDeletionMetadata() {
        IndexBlob main = blob("src/Main.java", StagedFileStatus.ADDED, "class Main {}\n", null);
        IndexBlob deleted = blob("src/Deleted.java", StagedFileStatus.DELETED,
                "DELETED_INDEX_SENTINEL\n", "class Deleted {}\n");
        IndexBlob unlisted = blob("src/Injected.java", StagedFileStatus.ADDED,
                "UNLISTED_INDEX_SENTINEL\n", null);
        CapturedStagedChange captured = capturedWithDeclaredFiles(List.of(main.file(), deleted.file()),
                List.of(main, deleted, unlisted));

        ProjectSnapshot snapshot = new StagedChangeContextBuilder(new CodeDefenseConfig(30, 4_096, 1_024))
                .build(captured);

        assertEquals(List.of(Path.of("src/Main.java")),
                snapshot.selectedFiles().stream().map(ProjectSnapshot.SelectedFile::relativePath).toList());
        assertFalse(snapshot.promptContent().contains("DELETED_INDEX_SENTINEL"));
        assertFalse(snapshot.promptContent().contains("UNLISTED_INDEX_SENTINEL"));
        assertTrue(snapshot.promptContent().contains("diff --staged src/Deleted.java\nstatus: DELETED"));
    }

    private static CapturedStagedChange captured(List<IndexBlob> blobs, String diff) {
        List<StagedChangeFile> files = blobs.stream().map(IndexBlob::file)
                .sorted(java.util.Comparator.comparing(file -> file.path().toString())).toList();
        return capturedWithDeclaredFiles(files, blobs, diff);
    }

    private static CapturedStagedChange capturedWithDeclaredFiles(List<StagedChangeFile> files, List<IndexBlob> blobs) {
        return capturedWithDeclaredFiles(files, blobs, "diff\n");
    }

    private static CapturedStagedChange capturedWithDeclaredFiles(List<StagedChangeFile> files, List<IndexBlob> blobs,
            String diff) {
        List<StagedChangeFile> sortedFiles = files.stream()
                .sorted(java.util.Comparator.comparing(file -> file.path().toString())).toList();
        return new CapturedStagedChange(new StagedChange(ROOT, "a".repeat(64), "b".repeat(40), "c".repeat(40),
                "d".repeat(64), sortedFiles, 8, 3), blobs, diff);
    }

    private static IndexBlob blob(String path, StagedFileStatus status, String index, String base) {
        StagedChangeFile file = new StagedChangeFile(Path.of(path), status, index == null ? 0 : 1,
                base == null ? 0 : 1);
        return new IndexBlob(file, Optional.ofNullable(index), false, Optional.ofNullable(base), false);
    }
}
