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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StagedChangeContextBuilderTest {
    private static final Path ROOT = Path.of("C:/workspace/demo").toAbsolutePath().normalize();

    @Test
    void rendersTheActualLargeFileHunkInsteadOfAnUnrelatedBlobPrefix() {
        StagedHunk hunk = hunk("src/LargeService.java", StagedFileStatus.MODIFIED, 1497, 7, 1497, 8,
                " contextBefore1();\n contextBefore2();\n contextBefore3();\n"
                        + "-nearbyContext();\n+nearbyContext();\n+changedLine();\n"
                        + " contextAfter1();\n contextAfter2();\n contextAfter3();");

        ProjectSnapshot snapshot = builder(10_000, 2_000).build(captured(List.of(hunk)));

        assertTrue(snapshot.promptContent().contains("STAGED_HUNK: src/LargeService.java"));
        assertTrue(snapshot.promptContent().contains("NEW_LINES: 1497-1504"));
        assertTrue(snapshot.promptContent().contains("changedLine();"));
        assertTrue(snapshot.promptContent().contains("nearbyContext();"));
        assertFalse(snapshot.promptContent().contains("UNRELATED_PREFIX_ONLY_DATA"));
        assertTrue(snapshot.selectedFiles().getFirst().containsEvidence(1497, 1504));
        assertFalse(snapshot.selectedFiles().getFirst().containsEvidence(1, 2));
    }

    @Test
    void buildsADeleteOnlySnapshotFromHeadHunkEvidence() {
        StagedHunk hunk = hunk("src/RemovedService.java", StagedFileStatus.DELETED, 7, 2, 0, 0,
                "-removedService();\n-oldContext();");

        ProjectSnapshot snapshot = builder(10_000, 2_000).build(captured(List.of(hunk)));

        assertEquals(1, snapshot.selectedFiles().size());
        assertTrue(snapshot.promptContent().contains("HEAD_HUNK: src/RemovedService.java"));
        assertTrue(snapshot.promptContent().contains("EVIDENCE_STATE: DELETED_FROM_INDEX"));
        assertTrue(snapshot.selectedFiles().getFirst().containsEvidence(7, 8));
    }

    @Test
    void redactsOnlySecretsInSelectedHunkBlocks() {
        StagedHunk hunk = hunk("src/App.java", StagedFileStatus.ADDED, 0, 0, 1, 1,
                "+password=TOPSECRET");

        ProjectSnapshot snapshot = builder(10_000, 2_000).build(captured(List.of(hunk)));

        assertTrue(snapshot.promptContent().contains("password=[REDACTED]"));
        assertFalse(snapshot.promptContent().contains("TOPSECRET"));
        assertEquals(1, snapshot.redactionCount());
    }

    @Test
    void appliesSelectionAndByteLimitsToHunkBlocks() {
        List<StagedHunk> hunks = java.util.stream.IntStream.range(0, 31)
                .mapToObj(index -> hunk("src/File%02d.java".formatted(index), StagedFileStatus.ADDED,
                        0, 0, 1, 1, "+class File%02d { String value = \"%s\"; }".formatted(index, "x".repeat(400))))
                .toList();
        CodeDefenseConfig config = new CodeDefenseConfig(30, 20_000, 300);

        ProjectSnapshot snapshot = new StagedChangeContextBuilder(config).build(captured(hunks));

        assertEquals(30, snapshot.selectedFiles().size());
        assertTrue(snapshot.selectedFiles().stream().allMatch(ProjectSnapshot.SelectedFile::truncated));
        assertTrue(snapshot.selectedFiles().stream().allMatch(file -> file.renderedBytes() <= config.maximumFileBlockBytes()));
        assertTrue(snapshot.promptBytes() <= config.maximumSnapshotBytes());
    }

    @Test
    void excludesUnsupportedAndExcludedPathsBeforePromptConstruction() {
        StagedHunk accepted = hunk("src/App.java", StagedFileStatus.ADDED, 0, 0, 1, 1, "+class App {}");
        StagedHunk excluded = hunk("node_modules/Dependency.java", StagedFileStatus.ADDED, 0, 0, 1, 1,
                "+DEPENDENCY_SECRET");
        StagedHunk unsupported = hunk("image.png", StagedFileStatus.ADDED, 0, 0, 1, 1, "+BINARY_SENTINEL");

        ProjectSnapshot snapshot = builder(10_000, 2_000).build(captured(List.of(accepted, excluded, unsupported)));

        assertEquals(List.of(Path.of("src/App.java")),
                snapshot.selectedFiles().stream().map(ProjectSnapshot.SelectedFile::relativePath).toList());
        assertFalse(snapshot.promptContent().contains("DEPENDENCY_SECRET"));
        assertFalse(snapshot.promptContent().contains("BINARY_SENTINEL"));
    }

    @Test
    void rejectsChangesWithNoEligibleHunkEvidence() {
        StagedHunk unsupported = hunk("image.png", StagedFileStatus.ADDED, 0, 0, 1, 1, "+not source");

        assertThrows(EmptyProjectSnapshotException.class, () -> builder(10_000, 2_000).build(captured(List.of(unsupported))));
    }

    @Test
    void rendersOnlySafePreviewMetadata() {
        StagedHunk hunk = hunk("src/App.java", StagedFileStatus.ADDED, 0, 0, 1, 1, "+class App {}");
        ProjectSnapshot snapshot = builder(10_000, 2_000).build(captured(List.of(hunk)));
        StringWriter text = new StringWriter();

        new StagedChangePreviewRenderer(new CodeDefenseConfig(30, 10_000, 2_000))
                .render(captured(List.of(hunk)).change(), snapshot, new PrintWriter(text));

        assertTrue(text.toString().contains("Mode: Staged change"));
        assertTrue(text.toString().contains("Index identity:"));
        assertFalse(text.toString().contains("class App"));
        assertFalse(text.toString().contains(ROOT.toString()));
    }

    @Test
    void rendersExactRenameTransitionAsMetadataWithoutWholeFileContent() {
        StagedChangeFile renamed = new StagedChangeFile(Path.of("src/NewName.java"),
                Optional.of(Path.of("src/OldName.java")), StagedFileStatus.RENAMED, 0, 0);
        StagedHunk modified = hunk("src/App.java", StagedFileStatus.MODIFIED, 1, 1, 1, 1,
                "-oldValue();\n+newValue();");
        List<StagedChangeFile> files = List.of(modified.file(), renamed);
        StagedChange change = new StagedChange(ROOT, "a".repeat(64), "b".repeat(40), "c".repeat(64),
                "d".repeat(64), files, 1, 1);

        ProjectSnapshot snapshot = builder(10_000, 2_000)
                .build(new CapturedStagedChange(change, List.of(modified)));

        assertTrue(snapshot.promptContent().contains("path: src/NewName.java"));
        assertTrue(snapshot.promptContent().contains("previousPath: src/OldName.java"));
        assertFalse(snapshot.promptContent().contains("INDEX_FILE: src/NewName.java"));
        assertFalse(snapshot.promptContent().contains("HEAD_FILE: src/OldName.java"));
    }

    private static StagedChangeContextBuilder builder(int snapshotBytes, int blockBytes) {
        return new StagedChangeContextBuilder(new CodeDefenseConfig(30, snapshotBytes, blockBytes));
    }

    private static CapturedStagedChange captured(List<StagedHunk> hunks) {
        List<StagedChangeFile> files = hunks.stream().map(StagedHunk::file)
                .sorted(java.util.Comparator.comparing(file -> file.path().toString())).toList();
        return new CapturedStagedChange(new StagedChange(ROOT, "a".repeat(64), "b".repeat(40), "c".repeat(64),
                "d".repeat(64), files, 1, 1), hunks);
    }

    private static StagedHunk hunk(String path, StagedFileStatus status, int oldStart, int oldCount,
            int newStart, int newCount, String content) {
        StagedChangeFile file = new StagedChangeFile(Path.of(path), status,
                status == StagedFileStatus.DELETED ? 0 : newCount, oldCount);
        return new StagedHunk(file, oldStart, oldCount, newStart, newCount, content, false);
    }
}
