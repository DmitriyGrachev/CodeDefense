package dev.codedefense.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedFileStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCliStagedChangeSourceTest {
    private static final String BASE = "a".repeat(40);
    private static final String OLD = "c".repeat(40);
    private static final String NEW = "d".repeat(40);

    @TempDir
    Path temporaryDirectory;

    @Test
    void capturesBoundedHunksWithoutMaterializingAGitTreeOrReadingTheWorkingTree() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project root"));
        String unrelatedPrefix = "UNRELATED_PREFIX_ONLY_DATA\n".repeat(1_100);
        Files.writeString(root.resolve("LargeService.java"), unrelatedPrefix + "WORKTREE_SENTINEL_MUST_NOT_APPEAR");
        FakeProcessExecutor executor = successfulExecutor(root);

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);
        ProjectSnapshot snapshot = new StagedChangeContextBuilder().build(captured);

        assertTrue(Files.size(root.resolve("LargeService.java")) > 24 * 1024);
        assertEquals(List.of("git", "-C", root.toString(), "rev-parse", "--show-toplevel"),
                executor.specifications().getFirst().command());
        assertTrue(executor.specifications().stream().allMatch(spec -> spec.standardInput().isEmpty()));
        assertTrue(executor.specifications().stream().allMatch(spec -> spec.workingDirectory().equals(root)));
        assertFalse(executor.specifications().stream().anyMatch(spec -> spec.command().contains("write-tree")));
        assertFalse(executor.specifications().stream().anyMatch(spec -> spec.command().contains("cat-file")));
        assertTrue(captured.change().indexIdentity().matches("[0-9a-f]{64}"));
        assertTrue(captured.change().diffFingerprint().matches("[0-9a-f]{64}"));
        assertEquals(List.of(StagedFileStatus.MODIFIED, StagedFileStatus.DELETED),
                captured.change().files().stream().map(file -> file.status()).toList());
        assertEquals(2, captured.hunks().size());
        assertTrue(captured.hunks().stream().anyMatch(hunk -> hunk.unifiedContent().contains("changedLine();")));
        assertFalse(captured.hunks().stream().anyMatch(hunk -> hunk.unifiedContent().contains("WORKTREE_SENTINEL")));
        assertTrue(snapshot.promptContent().contains("NEW 1501 | changedLine();"));
        assertTrue(snapshot.promptContent().contains("nearbyContext();"));
        assertFalse(snapshot.promptContent().contains("UNRELATED_PREFIX_ONLY_DATA"));
        assertTrue(executor.specifications().stream().filter(spec -> spec.command().contains("diff"))
                .allMatch(spec -> spec.command().contains("--no-ext-diff") && spec.command().contains("--no-textconv")));
        assertTrue(executor.specifications().stream().anyMatch(spec -> spec.command().equals(List.of(
                "git", "-C", root.toString(), "diff", "--cached", "--no-ext-diff", "--no-textconv",
                "--unified=3", "--no-color", "--", "src/LargeService.java"))));
    }

    @Test
    void retainsACompleteUnicodeCharacterAtATruncatedHunkBoundary() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("unicode"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.responses.put(hunkDiffKey("src/LargeService.java"), result(
                "@@ -1497,1 +1497,1 @@\n+value = \u0416\n", true));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor, 4, 1024).capture(root);

        assertTrue(captured.hunks().getFirst().unifiedContent().contains("\u0416"));
        assertFalse(captured.hunks().getFirst().unifiedContent().contains("\uFFFD"));
        assertTrue(captured.hunks().getFirst().truncated());
    }

    @Test
    void supportsHeadOnlyHunksForDeletedSourceFiles() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("deleted"));
        CapturedStagedChange captured = new GitCliStagedChangeSource(successfulExecutor(root)).capture(root);

        StagedHunk deleted = captured.hunks().stream()
                .filter(hunk -> hunk.file().status() == StagedFileStatus.DELETED).findFirst().orElseThrow();

        assertEquals(7, deleted.oldStartLine());
        assertEquals(2, deleted.oldLineCount());
        assertEquals(0, deleted.newLineCount());
        assertTrue(deleted.unifiedContent().contains("removedService();"));
    }

    @Test
    void rejectsMalformedRawOutputAndUnsafePathsWithSafeMessages() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("unsafe"));
        FakeProcessExecutor malformed = successfulExecutor(root);
        malformed.responses.put(rawDiffKey(), result(":100644 100644 " + OLD + " " + NEW + " M\0", false));

        GitChangeException malformedFailure = assertThrows(GitChangeException.class,
                () -> new GitCliStagedChangeSource(malformed).capture(root));

        assertFalse(malformedFailure.getMessage().contains(OLD));
        FakeProcessExecutor traversal = successfulExecutor(root);
        traversal.responses.put(rawDiffKey(), result(":000000 100644 " + zeros() + " " + NEW + " A\0../secret.java\0", false));
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(traversal).capture(root));
    }

    @Test
    void rejectsUnavailableHeadEmptyChangesAndUnsafeProcessResults() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("errors"));

        FakeProcessExecutor noHead = successfulExecutor(root);
        noHead.responses.put(commandKey("rev-parse", "--verify", "HEAD"), failed());
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(noHead).capture(root));

        FakeProcessExecutor empty = successfulExecutor(root);
        empty.responses.put(rawDiffKey(), result("", false));
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(empty).capture(root));

        FakeProcessExecutor timedOut = successfulExecutor(root);
        timedOut.responses.put(rawDiffKey(), new ProcessResult(-1, "", new byte[0], "", false, false, true, Duration.ZERO));
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(timedOut).capture(root));
    }

    @Test
    void capturesAnEmptyIndexIdentityWithoutWeakeningInitialCaptureValidation() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("empty-identity"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.responses.put(rawDiffKey(), result("", false));
        GitCliStagedChangeSource source = new GitCliStagedChangeSource(executor);

        assertFalse(source.captureIdentity(root).hasStagedChanges());
        assertThrows(GitChangeException.class, () -> source.capture(root));
    }

    @Test
    void excludesUnsupportedAndGitSpecialEntriesWithoutLaunchingHunkCapture() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("special"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.responses.put(rawDiffKey(), result(
                ":000000 120000 " + zeros() + " " + NEW + " A\0linked.java\0"
                        + ":000000 160000 " + zeros() + " " + OLD + " A\0submodule\0"
                        + ":000000 100644 " + zeros() + " " + OLD + " A\0image.png\0", false));
        executor.responses.put(numstatDiffKey(), result(
                "1\t0\tlinked.java\0" + "1\t0\tsubmodule\0" + "1\t0\timage.png\0", false));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertTrue(captured.hunks().isEmpty());
        assertTrue(executor.specifications().stream().noneMatch(spec -> spec.command().contains("--unified=3")));
    }

    private FakeProcessExecutor successfulExecutor(Path root) {
        FakeProcessExecutor executor = new FakeProcessExecutor();
        executor.responses.put(commandKey("rev-parse", "--show-toplevel"), result(root.toString(), false));
        executor.responses.put(commandKey("rev-parse", "--verify", "HEAD"), result(BASE, false));
        executor.responses.put(rawDiffKey(), result(
                ":100644 100644 " + OLD + " " + NEW + " M\0src/LargeService.java\0"
                        + ":100644 000000 " + OLD + " " + zeros() + " D\0src/RemovedService.java\0", false));
        executor.responses.put(numstatDiffKey(), result(
                "1\t1\tsrc/LargeService.java\0" + "0\t2\tsrc/RemovedService.java\0", false));
        executor.responses.put(hunkDiffKey("src/LargeService.java"), result(
                "diff --git a/src/LargeService.java b/src/LargeService.java\n"
                        + "@@ -1497,7 +1497,8 @@\n"
                        + " contextBefore1();\n contextBefore2();\n contextBefore3();\n"
                        + "-nearbyContext();\n+nearbyContext();\n+changedLine();\n"
                        + " contextAfter1();\n contextAfter2();\n contextAfter3();\n", false));
        executor.responses.put(hunkDiffKey("src/RemovedService.java"), result(
                "diff --git a/src/RemovedService.java b/src/RemovedService.java\n"
                        + "@@ -7,2 +0,0 @@\n-removedService();\n-oldContext();\n", false));
        return executor;
    }

    private static ProcessResult result(String stdout, boolean truncated) {
        return new ProcessResult(0, stdout, stdout.getBytes(StandardCharsets.UTF_8), "", truncated, false, false,
                Duration.ZERO);
    }

    private static ProcessResult failed() {
        return new ProcessResult(1, "", new byte[0], "private git failure", false, false, false, Duration.ZERO);
    }

    private static String commandKey(String... command) {
        return String.join("\u0001", command);
    }

    private static String rawDiffKey() {
        return commandKey("diff", "--cached", "--no-ext-diff", "--no-textconv", "--raw", "-z", "--no-abbrev",
                "--find-renames=100%");
    }

    private static String numstatDiffKey() {
        return commandKey("diff", "--cached", "--no-ext-diff", "--no-textconv", "--numstat", "-z");
    }

    private static String hunkDiffKey(String path) {
        return commandKey("diff", "--cached", "--no-ext-diff", "--no-textconv", "--unified=3", "--no-color", "--", path);
    }

    private static String zeros() {
        return "0".repeat(40);
    }

    private static final class FakeProcessExecutor implements ProcessExecutor {
        private final Map<String, ProcessResult> responses = new HashMap<>();
        private final List<ProcessSpec> specifications = new ArrayList<>();

        @Override
        public ProcessResult execute(ProcessSpec specification) {
            specifications.add(specification);
            List<String> command = specification.command();
            int gitIndex = command.indexOf("git");
            int operation = gitIndex + 3;
            return responses.getOrDefault(commandKey(command.subList(operation, command.size()).toArray(String[]::new)), failed());
        }

        private List<ProcessSpec> specifications() {
            return List.copyOf(specifications);
        }
    }
}
