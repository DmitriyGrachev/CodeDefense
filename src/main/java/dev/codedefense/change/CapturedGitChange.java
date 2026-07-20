package dev.codedefense.change;

import dev.codedefense.domain.GitChange;
import java.util.List;
import java.util.Objects;

public record CapturedGitChange(GitChange change, List<StagedHunk> hunks) {
    public CapturedGitChange {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(hunks, "hunks");
        if (hunks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("hunks contain null");
        hunks = List.copyOf(hunks);
    }
}
