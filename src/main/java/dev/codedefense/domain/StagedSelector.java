package dev.codedefense.domain;

public record StagedSelector() implements ChangeSelector {
    @Override public ChangeKind kind() { return ChangeKind.STAGED; }
}
