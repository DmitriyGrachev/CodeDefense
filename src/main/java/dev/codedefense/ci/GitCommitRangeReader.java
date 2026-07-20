package dev.codedefense.ci;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.CommitSelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GitCommitRangeReader {
    private static final int MAX_COMMITS = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final ProcessExecutor executor;

    public GitCommitRangeReader(ProcessExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public List<GitRangeCommit> read(Path requestedPath, String base, String head) {
        new CommitSelector(base);
        new CommitSelector(head);
        Path requested = normalizeDirectory(requestedPath);
        Path root = repositoryRoot(requested);
        String baseId = objectId(run(root, 4096, "rev-parse", "--verify", "--end-of-options", base + "^{commit}"));
        String headId = objectId(run(root, 4096, "rev-parse", "--verify", "--end-of-options", head + "^{commit}"));
        ProcessResult ancestor = execute(root, 4096, "merge-base", "--is-ancestor", baseId, headId);
        if (!successful(ancestor)) throw unavailable();
        String range = baseId + ".." + headId;
        List<String> ids = lines(run(root, 4096, "rev-list", "--reverse", "--max-count=51", range));
        if (ids.isEmpty() || ids.size() > MAX_COMMITS) throw unavailable();
        List<GitRangeCommit> commits = new ArrayList<>();
        for (String id : ids) {
            if (!id.matches("[0-9a-f]{40,64}")) throw unavailable();
            List<String> parents = List.of(run(root, 4096, "rev-list", "--parents", "-n", "1", id).split("\\s+"));
            if (parents.size() < 2 || !parents.getFirst().equals(id)) throw unavailable();
            String message = run(root, 16 * 1024, "show", "-s", "--format=%B", id);
            commits.add(new GitRangeCommit(id, parents.get(1), message));
        }
        return List.copyOf(commits);
    }

    private Path repositoryRoot(Path requested) {
        String value = run(requested, 4096, "rev-parse", "--show-toplevel").trim();
        try {
            Path root = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) throw unavailable();
            return root.toRealPath();
        } catch (Exception exception) {
            if (exception instanceof CiPassportException failure) throw failure;
            throw unavailable();
        }
    }

    private String run(Path root, int limit, String... arguments) {
        ProcessResult result = execute(root, limit, arguments);
        if (!successful(result)) throw unavailable();
        return result.stdout().replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private ProcessResult execute(Path root, int limit, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        try {
            return executor.execute(new ProcessSpec(command, root, Map.of(), "", TIMEOUT,
                    Duration.ofSeconds(1), limit, 4096));
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private static boolean successful(ProcessResult result) {
        return result.exitCode() == 0 && !result.timedOut() && !result.stdoutTruncated()
                && !result.stderrTruncated();
    }

    private static String objectId(String value) {
        String id = value.trim();
        if (!id.matches("[0-9a-f]{40,64}")) throw unavailable();
        return id;
    }

    private static List<String> lines(String value) {
        return value.isBlank() ? List.of() : value.lines().filter(line -> !line.isBlank()).toList();
    }

    private static Path normalizeDirectory(Path path) {
        Objects.requireNonNull(path, "path");
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) throw unavailable();
        return normalized;
    }

    private static CiPassportException unavailable() {
        return new CiPassportException("Git commit history is unavailable for Passport continuity checking.");
    }
}
