package dev.codedefense.ci;

import java.util.Objects;

public record CommitContinuityResult(
        String commitId, CiPassportStatus status, String declaredFingerprint, String actualFingerprint) {
    public CommitContinuityResult {
        commitId = requireId(commitId);
        Objects.requireNonNull(status, "status");
        declaredFingerprint = requireOptionalHash(declaredFingerprint, "declaredFingerprint");
        actualFingerprint = requireOptionalHash(actualFingerprint, "actualFingerprint");
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "commitId");
        if (!value.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException("commitId is invalid");
        return value;
    }

    private static String requireOptionalHash(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.isEmpty() && !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
