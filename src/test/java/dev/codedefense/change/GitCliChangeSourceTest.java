package dev.codedefense.change;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.RangeSelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCliChangeSourceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void commitCaptureUsesOnlyResolvedIdsForEveryDiffAndKeepsChangedHunk() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("repo"));
        FixtureExecutor executor = new FixtureExecutor(root);

        CapturedGitChange captured = new GitCliChangeSource(executor)
                .capture(root, new CommitSelector("feature/user-controlled"));

        assertEquals(ChangeKind.COMMIT, captured.change().kind());
        assertEquals(1, captured.change().files().size());
        assertTrue(captured.hunks().getFirst().unifiedContent().contains("changedLine();"));
        List<List<String>> diffCommands = executor.commands.stream().filter(c -> c.contains("diff")).toList();
        assertFalse(diffCommands.isEmpty());
        assertTrue(diffCommands.stream().allMatch(c -> c.contains(FixtureExecutor.BASE)
                && c.contains(FixtureExecutor.TARGET)));
        assertTrue(diffCommands.stream().noneMatch(c -> c.contains("feature/user-controlled")
                || c.contains("--cached")));
    }

    @Test
    void rangeCaptureUsesMergeBaseAndResolvedHead() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("range"));
        FixtureExecutor executor = new FixtureExecutor(root);

        CapturedGitChange captured = new GitCliChangeSource(executor)
                .capture(root, new RangeSelector("main", "topic"));

        assertEquals(ChangeKind.RANGE, captured.change().kind());
        assertEquals(FixtureExecutor.BASE, captured.change().baseCommit());
        assertEquals(FixtureExecutor.TARGET, captured.change().targetIdentity());
        assertTrue(executor.commands.stream().anyMatch(c -> c.contains("merge-base")));
    }

    private static final class FixtureExecutor implements ProcessExecutor {
        private static final String BASE = "a".repeat(40);
        private static final String TARGET = "b".repeat(40);
        private static final String OLD = "c".repeat(40);
        private static final String NEW = "d".repeat(40);
        private final Path root;
        private final List<List<String>> commands = new ArrayList<>();
        private int revParseCommitCalls;

        private FixtureExecutor(Path root) { this.root = root; }

        @Override public ProcessResult execute(ProcessSpec spec) {
            List<String> command = spec.command();
            commands.add(command);
            if (command.contains("--show-toplevel")) return ok(root.toString());
            if (command.contains("merge-base")) return ok(BASE);
            if (command.contains("rev-parse") && command.contains("--end-of-options")) {
                String argument = command.getLast();
                if (argument.endsWith("^1")) return ok(BASE);
                return ok(revParseCommitCalls++ == 0 ? (argument.startsWith("main") ? BASE : TARGET) : TARGET);
            }
            if (command.contains("--raw")) {
                return ok(":100644 100644 " + OLD + " " + NEW + " M\0src/App.java\0");
            }
            if (command.contains("--numstat")) return ok("1\t1\tsrc/App.java\0");
            if (command.contains("--unified=3")) return ok("@@ -1,1 +1,1 @@\n-oldLine();\n+changedLine();\n");
            throw new AssertionError("Unexpected command: " + command);
        }

        private ProcessResult ok(String output) {
            return new ProcessResult(0, output, "", false, false, false, Duration.ZERO);
        }
    }
}
