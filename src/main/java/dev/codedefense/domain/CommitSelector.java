package dev.codedefense.domain;

public record CommitSelector(String revision) implements ChangeSelector {
    public CommitSelector {
        revision = RevisionText.requireSafe(revision, "revision");
    }
    @Override public ChangeKind kind() { return ChangeKind.COMMIT; }
}
