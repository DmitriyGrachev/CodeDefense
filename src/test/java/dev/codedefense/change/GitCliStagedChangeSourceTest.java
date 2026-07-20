package dev.codedefense.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class GitCliStagedChangeSourceTest {
    private static final String BASE = "a".repeat(40);
    private static final String OLD = "c".repeat(40);
    private static final String NEW = "d".repeat(40);

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresEveryStagedChangeSourceToImplementMetadataOnlyInspection() throws Exception {
        assertFalse(StagedChangeSource.class.getMethod("inspect", Path.class).isDefault());
    }

    @Test
    void inspectsAllStagedMetadataWithoutReadingHunksOrBlobContent() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("inspection"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.responses.put(rawDiffKey(), result(
                ":000000 100644 " + zeros() + " " + NEW + " A\0src/AddedService.java\0"
                        + ":100644 100644 " + OLD + " " + NEW + " M\0src/ModifiedService.java\0", false));
        executor.responses.put(numstatDiffKey(), result(
                "2\t0\tsrc/AddedService.java\0" + "1\t1\tsrc/ModifiedService.java\0", false));

        StagedChange inspected = new GitCliStagedChangeSource(executor).inspect(root.resolve("."));

        String canonicalEntries = "000000\0" + "100644\0" + zeros() + "\0" + NEW + "\0ADDED\0"
                + "src/AddedService.java\0src/AddedService.java\0"
                + "100644\0" + "100644\0" + OLD + "\0" + NEW + "\0MODIFIED\0"
                + "src/ModifiedService.java\0src/ModifiedService.java";
        String expectedFingerprint = sha256("codedefense-staged-change-v2\0" + BASE + "\0" + canonicalEntries);
        assertEquals(root.toAbsolutePath().normalize(), inspected.repositoryRoot());
        assertEquals(2, inspected.files().size());
        assertEquals(List.of(
                new StagedChangeFile(Path.of("src/AddedService.java"), StagedFileStatus.ADDED, 2, 0),
                new StagedChangeFile(Path.of("src/ModifiedService.java"), StagedFileStatus.MODIFIED, 1, 1)),
                inspected.files());
        assertEquals(3, inspected.addedLines());
        assertEquals(1, inspected.deletedLines());
        assertEquals(expectedFingerprint, inspected.diffFingerprint());
        assertTrue(executor.specifications().stream().noneMatch(spec -> spec.command().contains("--unified=3")));
        assertTrue(executor.specifications().stream().noneMatch(spec -> spec.command().contains("cat-file")));
    }

    @Test
    void rejectsAnEmptyIndexDuringInspection() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("empty-inspection"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.responses.put(rawDiffKey(), result("", false));

        GitChangeException exception = assertThrows(GitChangeException.class,
                () -> new GitCliStagedChangeSource(executor).inspect(root));

        assertEquals(GitChangeException.Kind.NO_STAGED_CHANGE, exception.kind());
    }

    @Test
    void rejectsAnIndexThatChangesDuringInspection() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("changing-inspection"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.queue(rawDiffKey(), result(rawOutput("src/LargeService.java", NEW), false),
                result(rawOutput("src/LargeService.java", "e".repeat(40)), false));

        GitChangeException exception = assertThrows(GitChangeException.class,
                () -> new GitCliStagedChangeSource(executor).inspect(root));

        assertEquals(GitChangeException.Kind.CHANGED_DURING_CAPTURE, exception.kind());
    }

    @Test
    void retainsCaptureMutationProtectionWhileReadingHunks() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("changing-during-hunk-capture"));
        FakeProcessExecutor executor = successfulExecutor(root);
        String original = rawOutput("src/LargeService.java", NEW);
        String changed = rawOutput("src/LargeService.java", "e".repeat(40));
        executor.queue(rawDiffKey(), result(original, false), result(original, false), result(changed, false));

        GitChangeException exception = assertThrows(GitChangeException.class,
                () -> new GitCliStagedChangeSource(executor).capture(root));

        assertEquals(GitChangeException.Kind.CHANGED_DURING_CAPTURE, exception.kind());
    }

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
                "git", "-C", root.toString(), "--literal-pathspecs", "diff", "--cached", "--no-ext-diff",
                "--no-textconv", "--find-renames=100%", "--unified=3", "--no-color", "--",
                "src/LargeService.java"))));
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
    void passesEveryRepositoryPathAsALiteralPathspec() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("literal-pathspec"));
        FakeProcessExecutor executor = successfulExecutor(root);

        new GitCliStagedChangeSource(executor).capture(root);

        assertTrue(executor.specifications().stream()
                .filter(spec -> spec.command().contains("--unified=3"))
                .allMatch(spec -> spec.command().equals(List.of(
                        "git", "-C", root.toString(), "--literal-pathspecs", "diff", "--cached",
                        "--no-ext-diff", "--no-textconv", "--find-renames=100%", "--unified=3",
                        "--no-color", "--", spec.command().getLast()))));
    }

    @Test
    void rejectsAStagedChangeThatChangesDuringInitialCapture() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("changing-index"));
        FakeProcessExecutor executor = successfulExecutor(root);
        String original = rawOutput("src/LargeService.java", NEW);
        String changed = rawOutput("src/LargeService.java", "e".repeat(40));
        executor.queue(rawDiffKey(), result(original, false), result(changed, false));

        GitChangeException exception = assertThrows(GitChangeException.class,
                () -> new GitCliStagedChangeSource(executor).capture(root));

        assertEquals("Staged change changed during capture; retry.", exception.getMessage());
        assertFalse(exception.getMessage().contains(NEW));
        assertFalse(exception.getMessage().contains("e".repeat(40)));
    }

    @Test
    void launchesAtMostThirtyDeterministicallyPrioritizedHunkCommands() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("bounded-processes"));
        FakeProcessExecutor executor = successfulExecutor(root);
        StringBuilder raw = new StringBuilder();
        StringBuilder numstat = new StringBuilder();
        for (int index = 0; index < 31; index++) {
            String path = "src/File%02d.java".formatted(index);
            raw.append(":000000 100644 ").append(zeros()).append(' ').append(NEW)
                    .append(" A\0").append(path).append('\0');
            numstat.append("1\t0\t").append(path).append('\0');
            if (index < 30) {
                executor.responses.put(hunkDiffKey(path), result("@@ -0,0 +1,1 @@\n+class File" + index + " {}\n", false));
            }
        }
        executor.responses.put(rawDiffKey(), result(raw.toString(), false));
        executor.responses.put(numstatDiffKey(), result(numstat.toString(), false));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertEquals(30, captured.hunks().size());
        assertEquals(30, executor.specifications().stream()
                .filter(spec -> spec.command().contains("--unified=3")).count());
        assertFalse(captured.hunks().stream().anyMatch(hunk -> hunk.file().path().endsWith("File30.java")));
        assertEquals(31, captured.change().files().size());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void treatsGitMagicFileNamesLiterallyAndNeverCapturesExcludedSentinelContent() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("git-magic"));
        FakeProcessExecutor executor = successfulExecutor(root);
        List<String> literalNames = List.of("*.java", ":(top)node_modules/Secret.java", ":(glob)**/*.java");
        String excluded = "node_modules/Secret.java";
        StringBuilder raw = new StringBuilder();
        StringBuilder numstat = new StringBuilder();
        for (String path : java.util.stream.Stream.concat(literalNames.stream(), java.util.stream.Stream.of(excluded)).toList()) {
            raw.append(":000000 100644 ").append(zeros()).append(' ').append(NEW)
                    .append(" A\0").append(path).append('\0');
            numstat.append("1\t0\t").append(path).append('\0');
        }
        for (String path : literalNames) {
            executor.responses.put(hunkDiffKey(path), result("@@ -0,0 +1,1 @@\n+literal " + path + "\n", false));
            executor.responses.put(activePathspecHunkDiffKey(path),
                    result("@@ -0,0 +1,1 @@\n+EXCLUDED_SENTINEL\n", false));
        }
        executor.responses.put(rawDiffKey(), result(raw.toString(), false));
        executor.responses.put(numstatDiffKey(), result(numstat.toString(), false));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertEquals(literalNames.stream().sorted().toList(),
                captured.hunks().stream().map(hunk -> hunk.file().path().toString()).sorted().toList());
        assertFalse(captured.hunks().stream().anyMatch(hunk -> hunk.unifiedContent().contains("EXCLUDED_SENTINEL")));
        assertTrue(executor.specifications().stream().noneMatch(spec -> spec.command().getLast().equals(excluded)));
    }

    @Test
    void preservesBothExactRenamePathsWithoutCapturingTheWholeDestinationFile() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("rename"));
        FakeProcessExecutor executor = successfulExecutor(root);
        String oldPath = "src/OldName.java";
        String newPath = "src/NewName.java";
        executor.responses.put(rawDiffKey(), result(
                ":100644 100644 " + OLD + " " + OLD + " R100\0" + oldPath + "\0" + newPath + "\0", false));
        executor.responses.put(numstatDiffKey(), result("0\t0\t\0" + oldPath + "\0" + newPath + "\0", false));
        executor.responses.put(renameHunkDiffKey(oldPath, newPath), result("", false));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertEquals(oldPath, captured.change().files().getFirst().previousPath().orElseThrow().toString().replace('\\', '/'));
        assertEquals(newPath, captured.change().files().getFirst().path().toString().replace('\\', '/'));
        assertTrue(captured.hunks().isEmpty());
        assertTrue(executor.specifications().stream().anyMatch(spec -> spec.command().equals(List.of(
                "git", "-C", root.toString(), "--literal-pathspecs", "diff", "--cached", "--no-ext-diff",
                "--no-textconv", "--find-renames=100%", "--unified=3", "--no-color", "--", oldPath, newPath))));
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
        return commandKey("diff", "--cached", "--no-ext-diff", "--no-textconv", "--find-renames=100%",
                "--numstat", "-z");
    }

    private static String hunkDiffKey(String path) {
        return commandKey("--literal-pathspecs", "diff", "--cached", "--no-ext-diff", "--no-textconv",
                "--find-renames=100%", "--unified=3", "--no-color", "--", path);
    }

    private static String renameHunkDiffKey(String oldPath, String newPath) {
        return commandKey("--literal-pathspecs", "diff", "--cached", "--no-ext-diff", "--no-textconv",
                "--find-renames=100%", "--unified=3", "--no-color", "--", oldPath, newPath);
    }

    private static String activePathspecHunkDiffKey(String path) {
        return commandKey("diff", "--cached", "--no-ext-diff", "--no-textconv", "--unified=3", "--no-color",
                "--", path);
    }

    private static String zeros() {
        return "0".repeat(40);
    }

    private static String rawOutput(String path, String objectId) {
        return ":100644 100644 " + OLD + " " + objectId + " M\0" + path + "\0";
    }

    private static String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class FakeProcessExecutor implements ProcessExecutor {
        private final Map<String, ProcessResult> responses = new HashMap<>();
        private final Map<String, ArrayDeque<ProcessResult>> queuedResponses = new HashMap<>();
        private final List<ProcessSpec> specifications = new ArrayList<>();

        @Override
        public ProcessResult execute(ProcessSpec specification) {
            specifications.add(specification);
            List<String> command = specification.command();
            int gitIndex = command.indexOf("git");
            int operation = gitIndex + 3;
            String key = commandKey(command.subList(operation, command.size()).toArray(String[]::new));
            ArrayDeque<ProcessResult> queued = queuedResponses.get(key);
            if (queued != null && !queued.isEmpty()) {
                return queued.removeFirst();
            }
            return responses.getOrDefault(key, failed());
        }

        private void queue(String key, ProcessResult... results) {
            queuedResponses.put(key, new ArrayDeque<>(List.of(results)));
        }

        private List<ProcessSpec> specifications() {
            return List.copyOf(specifications);
        }
    }
}
