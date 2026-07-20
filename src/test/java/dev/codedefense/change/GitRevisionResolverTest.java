package dev.codedefense.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.RangeSelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitRevisionResolverTest {
    @TempDir Path temporaryDirectory;

    @Test
    void resolvesCommitAndItsParentBeforeCapture() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("repo"));
        FakeExecutor executor = new FakeExecutor(root,
                ok(root), ok("b".repeat(40)), ok("a".repeat(40)));

        ResolvedChangeSelector resolved = new GitRevisionResolver(executor)
                .resolve(root, new CommitSelector("feature/demo"));

        assertEquals(ChangeKind.COMMIT, resolved.kind());
        assertEquals("a".repeat(40), resolved.baseCommit());
        assertEquals("b".repeat(40), resolved.targetIdentity());
        assertEquals(List.of("git", "-C", root.toString(), "rev-parse", "--verify", "--end-of-options",
                "feature/demo^{commit}"), executor.commands.get(1));
        assertEquals(List.of("git", "-C", root.toString(), "rev-parse", "--verify", "--end-of-options",
                "b".repeat(40) + "^1"), executor.commands.get(2));
    }

    @Test
    void resolvesRangeToMergeBaseAndImmutableHead() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("range"));
        FakeExecutor executor = new FakeExecutor(root, ok(root), ok("a".repeat(40)),
                ok("b".repeat(40)), ok("c".repeat(40)));

        ResolvedChangeSelector resolved = new GitRevisionResolver(executor)
                .resolve(root, new RangeSelector("main", "topic"));

        assertEquals(ChangeKind.RANGE, resolved.kind());
        assertEquals("c".repeat(40), resolved.baseCommit());
        assertEquals("b".repeat(40), resolved.targetIdentity());
        assertEquals(List.of("git", "-C", root.toString(), "merge-base",
                "a".repeat(40), "b".repeat(40)), executor.commands.getLast());
    }

    @Test
    void rejectsRootCommitAndNeverLeaksGitOutput() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("root-commit"));
        FakeExecutor executor = new FakeExecutor(root, ok(root), ok("b".repeat(40)),
                new ProcessResult(128, "SECRET", "diagnostic SECRET", false, false, false, Duration.ZERO));

        GitChangeException failure = assertThrows(GitChangeException.class,
                () -> new GitRevisionResolver(executor).resolve(root, new CommitSelector("HEAD")));

        assertEquals(GitChangeException.Kind.UNSUPPORTED_CHANGE, failure.kind());
        assertFalse(failure.getMessage().contains("SECRET"));
    }

    @Test
    void rejectsTruncatedMalformedAndTimedOutResolution() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("failures"));
        for (ProcessResult result : List.of(
                new ProcessResult(0, "a".repeat(40), "", true, false, false, Duration.ZERO),
                ok("NOT-A-HASH"),
                new ProcessResult(-1, "", "", false, false, true, Duration.ZERO))) {
            FakeExecutor executor = new FakeExecutor(root, ok(root), result);
            assertThrows(GitChangeException.class,
                    () -> new GitRevisionResolver(executor).resolve(root, new CommitSelector("HEAD")));
        }
    }

    private static ProcessResult ok(Path value) { return ok(value.toAbsolutePath().normalize().toString()); }
    private static ProcessResult ok(String value) {
        return new ProcessResult(0, value + "\n", "", false, false, false, Duration.ZERO);
    }

    private static final class FakeExecutor implements ProcessExecutor {
        private final ArrayDeque<ProcessResult> results;
        private final List<List<String>> commands = new ArrayList<>();
        private FakeExecutor(Path ignored, ProcessResult... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }
        @Override public ProcessResult execute(ProcessSpec spec) {
            commands.add(spec.command());
            return results.removeFirst();
        }
    }
}
