package dev.codedefense.change;

import dev.codedefense.domain.ChangeKind;
import java.nio.file.Path;
import java.util.Objects;

public record ResolvedChangeSelector(
        ChangeKind kind,
        Path repositoryRoot,
        String baseCommit,
        String targetIdentity) {
    public ResolvedChangeSelector {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        baseCommit = requireId(baseCommit, "baseCommit");
        targetIdentity = kind == ChangeKind.STAGED
                ? requireHash(targetIdentity, "targetIdentity")
                : requireId(targetIdentity, "targetIdentity");
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{40,64}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String requireHash(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
