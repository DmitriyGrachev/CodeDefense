package dev.codedefense.domain;

public sealed interface ChangeSelector permits StagedSelector, CommitSelector, RangeSelector {
    ChangeKind kind();
}
