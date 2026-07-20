package dev.codedefense.ci;

import dev.codedefense.change.GitCliChangeSource;
import dev.codedefense.domain.CommitSelector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CommitPassportContinuityChecker {
    private final GitCommitRangeReader rangeReader;
    private final GitCliChangeSource changeSource;
    private final PassportTrailerParser trailerParser;

    public CommitPassportContinuityChecker(GitCommitRangeReader rangeReader,
            GitCliChangeSource changeSource, PassportTrailerParser trailerParser) {
        this.rangeReader = Objects.requireNonNull(rangeReader, "rangeReader");
        this.changeSource = Objects.requireNonNull(changeSource, "changeSource");
        this.trailerParser = Objects.requireNonNull(trailerParser, "trailerParser");
    }

    public PassportContinuityResult check(Path repository, String base, String head) {
        List<CommitContinuityResult> results = new ArrayList<>();
        for (GitRangeCommit commit : rangeReader.read(repository, base, head)) {
            PassportTrailer trailer = trailerParser.parse(commit.message());
            if (trailer.state() == PassportTrailer.State.MISSING) {
                results.add(new CommitContinuityResult(commit.commitId(), CiPassportStatus.MISSING, "", ""));
            } else if (trailer.state() == PassportTrailer.State.MALFORMED) {
                results.add(new CommitContinuityResult(commit.commitId(), CiPassportStatus.UNAVAILABLE, "", ""));
            } else {
                String actual = changeSource.capturePassportFingerprint(
                        repository, new CommitSelector(commit.commitId()));
                CiPassportStatus status = trailer.fingerprint().equals(actual)
                        ? CiPassportStatus.MATCHED : CiPassportStatus.MISMATCH;
                results.add(new CommitContinuityResult(
                        commit.commitId(), status, trailer.fingerprint(), actual));
            }
        }
        return new PassportContinuityResult(results);
    }
}
