package dev.codedefense.ci;

import java.util.List;
import java.util.Objects;

public record PassportContinuityResult(List<CommitContinuityResult> commits) {
    public PassportContinuityResult {
        Objects.requireNonNull(commits, "commits");
        if (commits.isEmpty() || commits.size() > 50 || commits.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("commits must contain between 1 and 50 entries");
        }
        commits = List.copyOf(commits);
    }

    public long count(CiPassportStatus status) {
        return commits.stream().filter(commit -> commit.status() == status).count();
    }

    public boolean allMatched() {
        return commits.stream().allMatch(commit -> commit.status() == CiPassportStatus.MATCHED);
    }

    public boolean unavailable() {
        return commits.stream().anyMatch(commit -> commit.status() == CiPassportStatus.UNAVAILABLE);
    }
}
