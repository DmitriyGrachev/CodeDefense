package dev.codedefense.domain;

import java.util.Objects;

public record GitChangeIdentity(
        ChangeKind kind,
        String baseCommit,
        String targetIdentity,
        String diffFingerprint) {
    public GitChangeIdentity {
        Objects.requireNonNull(kind, "kind");
        baseCommit = requireGitId(baseCommit, "baseCommit");
        targetIdentity = kind == ChangeKind.STAGED
                ? requireSha256(targetIdentity, "targetIdentity")
                : requireGitId(targetIdentity, "targetIdentity");
        diffFingerprint = requireSha256(diffFingerprint, "diffFingerprint");
    }
    private static String requireGitId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }
    private static String requireSha256(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }
}
