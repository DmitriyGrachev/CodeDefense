package dev.codedefense.change;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.GitChangeIdentity;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeIdentity;
import dev.codedefense.domain.StagedSelector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Generalized bounded capture built on the reviewed hunk-oriented staged parser. */
public final class GitCliChangeSource implements GitChangeSource {
    private final ProcessExecutor executor;
    private final GitRevisionResolver resolver;

    public GitCliChangeSource(ProcessExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.resolver = new GitRevisionResolver(executor);
    }

    @Override
    public CapturedGitChange capture(Path requestedPath, ChangeSelector selector) {
        Objects.requireNonNull(selector, "selector");
        if (selector instanceof StagedSelector) {
            CapturedStagedChange captured = new GitCliStagedChangeSource(executor).capture(requestedPath);
            return translate(captured, ChangeKind.STAGED, captured.change().indexIdentity());
        }
        ResolvedChangeSelector resolved = resolver.resolve(requestedPath, selector);
        ProcessExecutor immutableDiffExecutor = new ImmutableDiffExecutor(executor, resolved);
        CapturedStagedChange captured = new GitCliStagedChangeSource(immutableDiffExecutor)
                .capture(resolved.repositoryRoot());
        return translate(captured, resolved.kind(), resolved.targetIdentity());
    }

    @Override
    public GitChangeIdentity captureIdentity(Path requestedPath, ChangeSelector selector) {
        if (selector instanceof StagedSelector) {
            StagedChangeIdentity identity = new GitCliStagedChangeSource(executor).captureIdentity(requestedPath);
            return new GitChangeIdentity(ChangeKind.STAGED, identity.baseCommit(), identity.indexIdentity(),
                    identity.diffFingerprint());
        }
        return capture(requestedPath, selector).change().identity();
    }

    /** Returns the staged-format fingerprint for the immutable parent-to-commit diff. */
    public String capturePassportFingerprint(Path requestedPath, CommitSelector selector) {
        ResolvedChangeSelector resolved = resolver.resolve(requestedPath, Objects.requireNonNull(selector, "selector"));
        ProcessExecutor immutableDiffExecutor = new ImmutableDiffExecutor(executor, resolved);
        return new GitCliStagedChangeSource(immutableDiffExecutor)
                .captureIdentity(resolved.repositoryRoot()).diffFingerprint();
    }

    private static CapturedGitChange translate(CapturedStagedChange captured, ChangeKind kind, String target) {
        StagedChange staged = captured.change();
        String fingerprint = kind == ChangeKind.STAGED ? staged.diffFingerprint()
                : sha256("codedefense-git-change-v1\0" + kind + "\0" + staged.baseCommit() + "\0"
                        + target + "\0" + staged.diffFingerprint());
        GitChangeIdentity identity = new GitChangeIdentity(kind, staged.baseCommit(), target, fingerprint);
        GitChange change = new GitChange(staged.repositoryRoot(), staged.repositoryIdentityHash(), identity,
                staged.files(), staged.addedLines(), staged.deletedLines());
        return new CapturedGitChange(change, captured.hunks());
    }

    /** Rewrites only the reviewed staged diff token; unresolved user text is never present here. */
    private static final class ImmutableDiffExecutor implements ProcessExecutor {
        private final ProcessExecutor delegate;
        private final ResolvedChangeSelector selector;

        private ImmutableDiffExecutor(ProcessExecutor delegate, ResolvedChangeSelector selector) {
            this.delegate = delegate;
            this.selector = selector;
        }

        @Override public ProcessResult execute(ProcessSpec spec) {
            List<String> command = spec.command();
            if (command.size() >= 6 && command.get(3).equals("rev-parse")
                    && command.get(4).equals("--verify") && command.get(5).equals("HEAD")) {
                return new ProcessResult(0, selector.baseCommit() + "\n", "", false, false, false, Duration.ZERO);
            }
            int diff = command.indexOf("diff");
            int cached = command.indexOf("--cached");
            if (diff >= 0 && cached >= 0) {
                List<String> rewritten = new ArrayList<>(command);
                rewritten.remove(cached);
                rewritten.add(diff + 1, selector.baseCommit());
                rewritten.add(diff + 2, selector.targetIdentity());
                spec = new ProcessSpec(rewritten, spec.workingDirectory(), spec.environment(), spec.standardInput(),
                        spec.timeout(), spec.terminationGracePeriod(), spec.maximumStdoutBytes(),
                        spec.maximumStderrBytes());
            }
            return delegate.execute(spec);
        }
    }

    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
