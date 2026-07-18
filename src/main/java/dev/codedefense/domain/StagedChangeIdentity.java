package dev.codedefense.domain;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Immutable identity of a Git index state. Unlike {@link StagedChange}, it can represent an empty
 * staged index so verification and post-interview recapture can report an expired passport.
 */
public record StagedChangeIdentity(
        Path repositoryRoot,
        String repositoryIdentityHash,
        String baseCommit,
        String indexTree,
        String diffFingerprint,
        List<String> changedPathHashes) {
    private static final String SHA_256_PATTERN = "[0-9a-f]{64}";
    private static final String GIT_ID_PATTERN = "[0-9a-f]{40,64}";

    public StagedChangeIdentity {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        if (!repositoryRoot.isAbsolute() || !repositoryRoot.equals(repositoryRoot.normalize())) {
            throw new IllegalArgumentException("repositoryRoot must be normalized and absolute");
        }
        repositoryIdentityHash = requireMatch(repositoryIdentityHash, SHA_256_PATTERN, "repositoryIdentityHash");
        baseCommit = requireMatch(baseCommit, GIT_ID_PATTERN, "baseCommit");
        indexTree = requireMatch(indexTree, GIT_ID_PATTERN, "indexTree");
        diffFingerprint = requireMatch(diffFingerprint, SHA_256_PATTERN, "diffFingerprint");
        Objects.requireNonNull(changedPathHashes, "changedPathHashes");
        changedPathHashes = List.copyOf(changedPathHashes);
        String previous = null;
        for (String hash : changedPathHashes) {
            requireMatch(hash, SHA_256_PATTERN, "changedPathHash");
            if (previous != null && previous.compareTo(hash) >= 0) {
                throw new IllegalArgumentException("changedPathHashes must be sorted and unique");
            }
            previous = hash;
        }
    }

    public static StagedChangeIdentity from(StagedChange change) {
        Objects.requireNonNull(change, "change");
        return new StagedChangeIdentity(
                change.repositoryRoot(),
                change.repositoryIdentityHash(),
                change.baseCommit(),
                change.indexTree(),
                change.diffFingerprint(),
                change.files().stream().map(StagedChangeFile::path).map(StagedChangeIdentity::pathHash).sorted().toList());
    }

    public boolean hasStagedChanges() {
        return !changedPathHashes.isEmpty();
    }

    public static String pathHash(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(path.toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireMatch(String value, String pattern, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return value;
    }
}
