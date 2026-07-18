package dev.codedefense.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
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
    private static final String TREE = "b".repeat(40);
    private static final String OLD = "c".repeat(40);
    private static final String NEW = "d".repeat(40);
    private static final String RENAMED = "e".repeat(40);

    @TempDir
    Path temporaryDirectory;

    @Test
    void capturesTokenizedIndexObjectsAndNeverUsesWorkingTreeContent() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project root"));
        Files.writeString(root.resolve("App.java"), "WORKTREE_SENTINEL_MUST_NOT_APPEAR");
        FakeProcessExecutor executor = successfulExecutor(root);

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertEquals(List.of("git", "-C", root.toString(), "rev-parse", "--show-toplevel"),
                executor.specifications().getFirst().command());
        assertTrue(executor.specifications().stream().allMatch(spec -> spec.standardInput().isEmpty()));
        assertTrue(executor.specifications().stream().allMatch(spec -> spec.workingDirectory().equals(root)));
        assertEquals(TREE, captured.change().indexTree());
        assertEquals(sha256("codedefense-staged-change-v1\0" + BASE + "\0" + TREE),
                captured.change().diffFingerprint());
        assertEquals(List.of("App.java", "Gone.java", "New.java", "Renamed.java"),
                captured.change().files().stream().map(file -> file.path().toString()).toList());
        assertEquals(List.of(StagedFileStatus.MODIFIED, StagedFileStatus.DELETED,
                        StagedFileStatus.ADDED, StagedFileStatus.RENAMED),
                captured.change().files().stream().map(file -> file.status()).toList());
        assertTrue(captured.blobs().stream().anyMatch(blob -> blob.indexContent().orElse("").contains("INDEX_APP")));
        assertFalse(captured.blobs().stream().anyMatch(blob -> blob.indexContent().orElse("").contains("WORKTREE_SENTINEL")));
        assertTrue(executor.specifications().stream().allMatch(spec -> spec.command().stream()
                .noneMatch(token -> token.contains("--ext-diff") || token.contains("textconv") || token.contains("sh -c"))));
    }

    @Test
    void readsUnicodePrefixWithoutCorruptingCompleteFinalCharacter() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("unicode"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.blobs.put(NEW, result("abР", true));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor, 4, 1024).capture(root);

        assertEquals("abР", captured.blobs().stream()
                .filter(blob -> blob.file().path().toString().equals("New.java"))
                .findFirst().orElseThrow().indexContent().orElseThrow());
        assertTrue(captured.blobs().stream()
                .filter(blob -> blob.file().path().toString().equals("New.java"))
                .findFirst().orElseThrow().indexTruncated());
    }

    @Test
    void trimsOnlyAnIncompleteUnicodeSequenceAtTheTruncatedBlobBoundary() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("truncated-unicode"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.blobs.put(NEW, rawResult(new byte[] {'a', 'b', (byte) 0xD0}, true));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertEquals("ab", captured.blobs().stream()
                .filter(blob -> blob.file().path().toString().equals("New.java"))
                .findFirst().orElseThrow().indexContent().orElseThrow());
    }

    @Test
    void transferObjectStringFormsDoNotExposeBlobOrDiffContent() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("safe-string"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.blobs.put(NEW, result("private-index-source", false));
        executor.responses.put(commandKey("diff", "--cached", "--no-ext-diff", "--no-color", "--full-index", "--binary"),
                result("private-canonical-diff", false));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertFalse(captured.toString().contains("private-index-source"));
        assertFalse(captured.toString().contains("private-canonical-diff"));
        assertFalse(captured.blobs().getFirst().toString().contains("private-index-source"));
    }

    @Test
    void rejectsMalformedRawOutputAndUnsafePathsWithSafeMessages() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("unsafe"));
        FakeProcessExecutor malformed = successfulExecutor(root);
        malformed.responses.put(commandKey("diff", "--cached", "--raw", "-z", "--no-abbrev", "--find-renames=100%"),
                result(":100644 100644 " + OLD + " " + NEW + " M\0", false));

        GitChangeException malformedFailure = assertThrows(GitChangeException.class,
                () -> new GitCliStagedChangeSource(malformed).capture(root));
        assertFalse(malformedFailure.getMessage().contains(OLD));

        FakeProcessExecutor traversal = successfulExecutor(root);
        traversal.responses.put(commandKey("diff", "--cached", "--raw", "-z", "--no-abbrev", "--find-renames=100%"),
                result(":000000 100644 " + zeros() + " " + NEW + " A\0../secret.java\0", false));
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(traversal).capture(root));
    }

    @Test
    void rejectsUnavailableHeadEmptyChangesAndUnsafeProcessResults() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("errors"));

        FakeProcessExecutor noHead = successfulExecutor(root);
        noHead.responses.put(commandKey("rev-parse", "--verify", "HEAD"), failed());
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(noHead).capture(root));

        FakeProcessExecutor empty = successfulExecutor(root);
        empty.responses.put(commandKey("diff", "--cached", "--raw", "-z", "--no-abbrev", "--find-renames=100%"), result("", false));
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(empty).capture(root));

        FakeProcessExecutor timedOut = successfulExecutor(root);
        timedOut.responses.put(commandKey("write-tree"), new ProcessResult(-1, "", new byte[0], "", false, false, true, Duration.ZERO));
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(timedOut).capture(root));

        FakeProcessExecutor truncated = successfulExecutor(root);
        truncated.responses.put(commandKey("write-tree"), new ProcessResult(0, TREE, TREE.getBytes(StandardCharsets.UTF_8), "", true, false, false, Duration.ZERO));
        assertThrows(GitChangeException.class, () -> new GitCliStagedChangeSource(truncated).capture(root));
    }

    @Test
    void excludesUnsupportedAndGitSpecialEntriesInsteadOfReadingThem() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("special"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.responses.put(commandKey("diff", "--cached", "--raw", "-z", "--no-abbrev", "--find-renames=100%"), result(
                ":000000 120000 " + zeros() + " " + NEW + " A\0linked.java\0"
                        + ":000000 160000 " + zeros() + " " + RENAMED + " A\0submodule\0"
                        + ":000000 100644 " + zeros() + " " + OLD + " A\0image.png\0", false));
        executor.responses.put(commandKey("diff", "--cached", "--numstat", "-z", "--no-ext-diff"),
                result("1\t0\tlinked.java\0" + "1\t0\tsubmodule\0" + "1\t0\timage.png\0", false));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertTrue(captured.blobs().isEmpty());
        assertTrue(executor.specifications().stream().noneMatch(spec -> spec.command().contains("cat-file")));
    }

    @Test
    void excludesRegularToSymlinkTransitionsAndFilesBelowExcludedDirectories() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("excluded-components"));
        FakeProcessExecutor executor = successfulExecutor(root);
        executor.responses.put(commandKey("diff", "--cached", "--raw", "-z", "--no-abbrev", "--find-renames=100%"), result(
                ":100644 120000 " + OLD + " " + NEW + " M\0App.java\0"
                        + ":000000 100644 " + zeros() + " " + RENAMED + " A\0node_modules/Dependency.java\0"
                        + ":000000 100644 " + zeros() + " " + NEW + " A\0target/generated/Generated.java\0"
                        + ":000000 100644 " + zeros() + " " + OLD + " A\0.git/hooks/Hook.java\0", false));
        executor.responses.put(commandKey("diff", "--cached", "--numstat", "-z", "--no-ext-diff"), result(
                "1\t1\tApp.java\0" + "1\t0\tnode_modules/Dependency.java\0"
                        + "1\t0\ttarget/generated/Generated.java\0" + "1\t0\t.git/hooks/Hook.java\0", false));

        CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(root);

        assertTrue(captured.blobs().isEmpty());
        assertTrue(executor.specifications().stream().noneMatch(spec -> spec.command().contains("cat-file")));
    }

    private FakeProcessExecutor successfulExecutor(Path root) {
        FakeProcessExecutor executor = new FakeProcessExecutor();
        executor.responses.put(commandKey("rev-parse", "--show-toplevel"), result(root.toString(), false));
        executor.responses.put(commandKey("rev-parse", "--verify", "HEAD"), result(BASE, false));
        executor.responses.put(commandKey("write-tree"), result(TREE, false));
        executor.responses.put(commandKey("diff", "--cached", "--raw", "-z", "--no-abbrev", "--find-renames=100%"), result(
                ":100644 100644 " + OLD + " " + NEW + " M\0App.java\0"
                        + ":100644 000000 " + OLD + " " + zeros() + " D\0Gone.java\0"
                        + ":000000 100644 " + zeros() + " " + NEW + " A\0New.java\0"
                        + ":100644 100644 " + OLD + " " + RENAMED + " R100\0Old.java\0Renamed.java\0", false));
        executor.responses.put(commandKey("diff", "--cached", "--numstat", "-z", "--no-ext-diff"), result(
                "2\t1\tApp.java\0" + "0\t3\tGone.java\0" + "4\t0\tNew.java\0"
                        + "1\t1\t\0Old.java\0Renamed.java\0", false));
        executor.responses.put(commandKey("diff", "--cached", "--no-ext-diff", "--no-color", "--full-index", "--binary"), result("canonical diff", false));
        executor.blobs.put(OLD, result("BASE_APP", false));
        executor.blobs.put(NEW, result("INDEX_APP", false));
        executor.blobs.put(RENAMED, result("INDEX_RENAMED", false));
        return executor;
    }

    private static ProcessResult result(String stdout, boolean truncated) {
        return new ProcessResult(0, stdout, stdout.getBytes(StandardCharsets.UTF_8), "", truncated, false, false, Duration.ZERO);
    }

    private static ProcessResult rawResult(byte[] stdout, boolean truncated) {
        return new ProcessResult(0, "", stdout, "", truncated, false, false, Duration.ZERO);
    }

    private static ProcessResult failed() {
        return new ProcessResult(1, "", new byte[0], "private git failure", false, false, false, Duration.ZERO);
    }

    private static String commandKey(String... command) {
        return String.join("\u0001", command);
    }

    private static String zeros() {
        return "0".repeat(40);
    }

    private static String sha256(String input) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeProcessExecutor implements ProcessExecutor {
        private final Map<String, ProcessResult> responses = new HashMap<>();
        private final Map<String, ProcessResult> blobs = new HashMap<>();
        private final List<ProcessSpec> specifications = new ArrayList<>();

        @Override
        public ProcessResult execute(ProcessSpec specification) {
            specifications.add(specification);
            List<String> command = specification.command();
            if (command.size() >= 2 && command.get(command.size() - 2).equals("blob")) {
                return blobs.getOrDefault(command.getLast(), failed());
            }
            int gitIndex = command.indexOf("git");
            int operation = gitIndex + 3;
            return responses.getOrDefault(commandKey(command.subList(operation, command.size()).toArray(String[]::new)), failed());
        }

        private List<ProcessSpec> specifications() {
            return List.copyOf(specifications);
        }
    }
}
