package dev.codedefense.domain;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record StagedChange(
        Path repositoryRoot,
        String repositoryIdentityHash,
        String baseCommit,
        String indexTree,
        String diffFingerprint,
        List<StagedChangeFile> files,
        int addedLines,
        int deletedLines) {
    private static final String SHA_256_PATTERN = "[0-9a-f]{64}";
    private static final String GIT_ID_PATTERN = "[0-9a-f]{40,64}";

    public StagedChange {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        if (!repositoryRoot.isAbsolute() || !repositoryRoot.equals(repositoryRoot.normalize())) {
            throw new IllegalArgumentException("repositoryRoot must be normalized and absolute");
        }
        repositoryIdentityHash = requireMatch(repositoryIdentityHash, SHA_256_PATTERN, "repositoryIdentityHash");
        baseCommit = requireMatch(baseCommit, GIT_ID_PATTERN, "baseCommit");
        indexTree = requireMatch(indexTree, GIT_ID_PATTERN, "indexTree");
        diffFingerprint = requireMatch(diffFingerprint, SHA_256_PATTERN, "diffFingerprint");
        Objects.requireNonNull(files, "files");
        files = List.copyOf(files);
        if (files.isEmpty() || files.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("files must be nonempty and cannot contain null");
        }
        Set<String> paths = new HashSet<>();
        String previous = null;
        for (StagedChangeFile file : files) {
            String current = portablePath(file.path());
            if (!paths.add(current) || (previous != null && previous.compareTo(current) >= 0)) {
                throw new IllegalArgumentException("files must be unique and sorted by path");
            }
            previous = current;
        }
        if (addedLines < 0 || deletedLines < 0) {
            throw new IllegalArgumentException("line totals cannot be negative");
        }
    }

    private static String requireMatch(String value, String pattern, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return value;
    }

    private static String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    @Override
    public String toString() {
        return "StagedChange[repositoryRoot=%s, repositoryIdentityHash=%s, baseCommit=%s, indexTree=%s, diffFingerprint=%s, fileCount=%d, addedLines=%d, deletedLines=%d]"
                .formatted(repositoryRoot, repositoryIdentityHash, baseCommit, indexTree, diffFingerprint,
                        files.size(), addedLines, deletedLines);
    }
}
