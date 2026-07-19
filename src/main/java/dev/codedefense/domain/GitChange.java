package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One immutable, resolved Git change independent of how it was selected. */
public record GitChange(
        Path repositoryRoot,
        String repositoryIdentityHash,
        GitChangeIdentity identity,
        List<StagedChangeFile> files,
        int addedLines,
        int deletedLines) {
    public GitChange {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        if (!repositoryRoot.isAbsolute() || !repositoryRoot.equals(repositoryRoot.normalize())) {
            throw new IllegalArgumentException("repositoryRoot must be normalized and absolute");
        }
        Objects.requireNonNull(repositoryIdentityHash, "repositoryIdentityHash");
        if (!repositoryIdentityHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("repositoryIdentityHash is invalid");
        }
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(files, "files");
        files = List.copyOf(files);
        if (files.isEmpty() || files.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("files must be nonempty");
        }
        Set<String> paths = new HashSet<>();
        String previous = null;
        for (StagedChangeFile file : files) {
            String current = file.path().toString().replace('\\', '/');
            if (!paths.add(current) || previous != null && previous.compareTo(current) >= 0) {
                throw new IllegalArgumentException("files must be unique and sorted by path");
            }
            previous = current;
        }
        if (addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException("line totals cannot be negative");
        }
    }

    public ChangeKind kind() { return identity.kind(); }
    public String baseCommit() { return identity.baseCommit(); }
    public String targetIdentity() { return identity.targetIdentity(); }
    public String diffFingerprint() { return identity.diffFingerprint(); }

    @Override public String toString() {
        return "GitChange[kind=%s, repositoryIdentityHash=%s, identity=%s, fileCount=%d, addedLines=%d, deletedLines=%d]"
                .formatted(kind(), repositoryIdentityHash, identity, files.size(), addedLines, deletedLines);
    }
}
