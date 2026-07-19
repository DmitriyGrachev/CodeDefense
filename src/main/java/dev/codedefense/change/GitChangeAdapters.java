package dev.codedefense.change;

import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.StagedChange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class GitChangeAdapters {
    private GitChangeAdapters() { }

    public static StagedChange asStagedChange(GitChange change) {
        return new StagedChange(change.repositoryRoot(), change.repositoryIdentityHash(), change.baseCommit(),
                sha256(change.targetIdentity()), change.diffFingerprint(), change.files(), change.addedLines(),
                change.deletedLines());
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
