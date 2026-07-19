package dev.codedefense.change;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.RangeSelector;
import dev.codedefense.domain.StagedSelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves user-controlled revision text once, before any diff command is built. */
public final class GitRevisionResolver {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final ProcessExecutor executor;

    public GitRevisionResolver(ProcessExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public ResolvedChangeSelector resolve(Path requestedPath, ChangeSelector selector) {
        Objects.requireNonNull(selector, "selector");
        Path requested = normalizeDirectory(requestedPath);
        Path root = resolveRoot(requested);
        if (selector instanceof StagedSelector) {
            throw new IllegalArgumentException("Staged identity is resolved during index capture");
        }
        if (selector instanceof CommitSelector commit) {
            String target = resolveCommit(root, commit.revision());
            ProcessResult parent = run(root, "rev-parse", "--verify", "--end-of-options", target + "^1");
            if (!successful(parent)) {
                throw new GitChangeException(GitChangeException.Kind.UNSUPPORTED_CHANGE);
            }
            return new ResolvedChangeSelector(selector.kind(), root, objectId(parent), target);
        }
        RangeSelector range = (RangeSelector) selector;
        String base = resolveCommit(root, range.baseRevision());
        String head = resolveCommit(root, range.headRevision());
        ProcessResult mergeBase = run(root, "merge-base", base, head);
        if (!successful(mergeBase)) {
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
        return new ResolvedChangeSelector(selector.kind(), root, objectId(mergeBase), head);
    }

    private String resolveCommit(Path root, String revision) {
        ProcessResult result = run(root, "rev-parse", "--verify", "--end-of-options", revision + "^{commit}");
        if (!successful(result)) {
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
        return objectId(result);
    }

    private Path resolveRoot(Path requested) {
        ProcessResult result = run(requested, "rev-parse", "--show-toplevel");
        if (!successful(result)) {
            throw new GitChangeException(GitChangeException.Kind.INVALID_REPOSITORY);
        }
        try {
            Path root = Path.of(result.stdout().trim()).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) throw new IllegalArgumentException();
            return root;
        } catch (RuntimeException exception) {
            throw new GitChangeException(GitChangeException.Kind.INVALID_REPOSITORY);
        }
    }

    private ProcessResult run(Path root, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        try {
            return executor.execute(new ProcessSpec(command, root, Map.of(), "", TIMEOUT,
                    Duration.ofSeconds(1), 4096, 4096));
        } catch (RuntimeException exception) {
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
    }

    private static boolean successful(ProcessResult result) {
        return result.exitCode() == 0 && !result.timedOut() && !result.stdoutTruncated()
                && !result.stderrTruncated();
    }

    private static String objectId(ProcessResult result) {
        String value = result.stdout().trim();
        if (!value.matches("[0-9a-f]{40,64}")) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
        return value;
    }

    private static Path normalizeDirectory(Path path) {
        Objects.requireNonNull(path, "path");
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new GitChangeException(GitChangeException.Kind.INVALID_REPOSITORY);
        }
        return normalized;
    }
}
