package dev.codedefense.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.ai.AppServerFileChange;
import dev.codedefense.change.CapturedGitChange;
import dev.codedefense.change.StagedHunk;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.CodexProvenanceStatus;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.GitChangeIdentity;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexChangeMatcherTest {
    @TempDir Path repository;
    private final CodexChangeMatcher matcher = new CodexChangeMatcher();

    @Test
    void matchesExactNormalizedHunksAcrossLineEndings() {
        CapturedGitChange git = captured(List.of(file("src/App.java", StagedFileStatus.MODIFIED)),
                List.of(hunk(file("src/App.java", StagedFileStatus.MODIFIED), 10, 1, 10, 1,
                        "-old\n+new")));

        ProvenanceMatch match = matcher.match(git, List.of(change("2", "src/App.java", "update",
                "--- a/src/App.java\r\n+++ b/src/App.java\r\n@@ -10 +10 @@\r\n-old\r\n+new")));

        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH, match.status());
        assertEquals(List.of("src/App.java"), match.matchedRelativePaths());
    }

    @Test
    void reportsPartialAndRejectsSamePathDifferentContent() {
        StagedChangeFile a = file("src/A.java", StagedFileStatus.MODIFIED);
        StagedChangeFile b = file("src/B.java", StagedFileStatus.MODIFIED);
        CapturedGitChange git = captured(List.of(a, b), List.of(
                hunk(a, 1, 1, 1, 1, "-oldA\n+newA"),
                hunk(b, 1, 1, 1, 1, "-oldB\n+newB")));

        ProvenanceMatch partial = matcher.match(git, List.of(
                change("1", "src/A.java", "update", "@@ -1 +1 @@\n-oldA\n+newA"),
                change("2", "src/B.java", "update", "@@ -1 +1 @@\n-oldB\n+different")));

        assertEquals(CodexProvenanceStatus.PARTIAL_PATH_MATCH, partial.status());
        assertEquals(List.of("src/A.java"), partial.matchedRelativePaths());
    }

    @Test
    void usesLastOrderedEditForOnePathAndRedactsSecretsBeforeHashing() {
        StagedChangeFile file = file("config/app.yml", StagedFileStatus.MODIFIED);
        CapturedGitChange git = captured(List.of(file), List.of(
                hunk(file, 1, 1, 1, 1, "-password=old\n+password=new-secret")));

        ProvenanceMatch match = matcher.match(git, List.of(
                change("a", "config/app.yml", "update", "@@ -1 +1 @@\n-password=wrong\n+password=wrong"),
                change("z", "config/app.yml", "update",
                        "@@ -1 +1 @@\n-password=different-old\n+password=different-new")));

        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH, match.status());
    }

    @Test
    void coversAddedDeletedAndRenameStatusAndRejectsMaliciousHeaders() {
        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH,
                matcher.match(single("src/New.java", StagedFileStatus.ADDED, 0, 0, 1, 1, "+new"),
                        List.of(change("1", "src/New.java", "create", "@@ -0,0 +1 @@\n+new"))).status());
        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH,
                matcher.match(single("src/Old.java", StagedFileStatus.DELETED, 1, 1, 0, 0, "-old"),
                        List.of(change("1", "src/Old.java", "delete", "@@ -1 +0,0 @@\n-old"))).status());
        StagedChangeFile renamed = new StagedChangeFile(Path.of("src/NewName.java"),
                java.util.Optional.of(Path.of("src/OldName.java")), StagedFileStatus.RENAMED, 1, 1);
        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH,
                matcher.match(captured(List.of(renamed), List.of(hunk(renamed, 1, 1, 1, 1, "-old\n+new"))),
                        List.of(change("1", "src/NewName.java", "move", "@@ -1 +1 @@\n-old\n+new"))).status());
        assertThrows(IllegalArgumentException.class, () -> matcher.match(
                single("src/App.java", StagedFileStatus.MODIFIED, 1, 1, 1, 1, "-old\n+new"),
                List.of(change("1", "src/App.java", "update",
                        "--- ../../secret\n+++ b/src/App.java\n@@ -1 +1 @@\n-old\n+new"))));
    }

    private CapturedGitChange single(String path, StagedFileStatus status,
            int oldStart, int oldCount, int newStart, int newCount, String content) {
        StagedChangeFile file = file(path, status);
        return captured(List.of(file), List.of(hunk(file, oldStart, oldCount, newStart, newCount, content)));
    }

    private CapturedGitChange captured(List<StagedChangeFile> files, List<StagedHunk> hunks) {
        int added = files.stream().mapToInt(StagedChangeFile::addedLines).sum();
        int deleted = files.stream().mapToInt(StagedChangeFile::deletedLines).sum();
        GitChange change = new GitChange(repository.toAbsolutePath().normalize(), "a".repeat(64),
                new GitChangeIdentity(ChangeKind.STAGED, "b".repeat(40), "c".repeat(64), "d".repeat(64)),
                files, added, deleted);
        return new CapturedGitChange(change, hunks);
    }

    private static StagedChangeFile file(String path, StagedFileStatus status) {
        int added = status == StagedFileStatus.DELETED ? 0 : 1;
        int deleted = status == StagedFileStatus.ADDED ? 0 : 1;
        return new StagedChangeFile(Path.of(path), status, added, deleted);
    }

    private static StagedHunk hunk(StagedChangeFile file, int oldStart, int oldCount,
            int newStart, int newCount, String content) {
        return new StagedHunk(file, oldStart, oldCount, newStart, newCount, content, false);
    }

    private static AppServerFileChange change(String id, String path, String kind, String patch) {
        return new AppServerFileChange(id, path, kind, patch);
    }
}
