package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class GitChangeTest {
    @Test
    void parsesThreeDotRangeAndPreservesTokens() {
        RangeSelector selector = RangeSelector.parse("origin/main...feature/demo");
        assertEquals("origin/main", selector.baseRevision());
        assertEquals("feature/demo", selector.headRevision());
        assertEquals(ChangeKind.RANGE, selector.kind());
        assertEquals(ChangeKind.COMMIT, new CommitSelector("HEAD~1").kind());
        assertEquals(ChangeKind.STAGED, new StagedSelector().kind());
    }

    @Test
    void rejectsUnsafeOrAmbiguousRevisionText() {
        for (String invalid : new String[] {"", " ", "--help", "HEAD\nmain", "a..b", "a...b...c"}) {
            if (invalid.contains("..." ) || invalid.contains("..")) {
                assertThrows(IllegalArgumentException.class, () -> RangeSelector.parse(invalid));
            } else {
                assertThrows(IllegalArgumentException.class, () -> new CommitSelector(invalid));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> RangeSelector.parse("main..HEAD"));
        assertThrows(IllegalArgumentException.class, () -> new RangeSelector("-main", "HEAD"));
    }

    @Test
    void validatesGeneralizedIdentity() {
        GitChangeIdentity identity = new GitChangeIdentity(ChangeKind.COMMIT,
                "a".repeat(40), "b".repeat(40), "c".repeat(64));
        assertEquals(ChangeKind.COMMIT, identity.kind());
        assertThrows(IllegalArgumentException.class, () -> new GitChangeIdentity(ChangeKind.RANGE,
                "A".repeat(40), "b".repeat(40), "c".repeat(64)));
    }

    @Test
    void validatesSortedFilesAndLineTotals() {
        GitChangeIdentity identity = new GitChangeIdentity(ChangeKind.RANGE,
                "a".repeat(40), "b".repeat(40), "c".repeat(64));
        StagedChangeFile file = new StagedChangeFile(Path.of("src/App.java"),
                StagedFileStatus.MODIFIED, 2, 1);
        GitChange change = new GitChange(Path.of(".").toAbsolutePath().normalize(),
                "d".repeat(64), identity, List.of(file), 2, 1);
        assertEquals(ChangeKind.RANGE, change.kind());
        assertThrows(IllegalArgumentException.class, () -> new GitChange(change.repositoryRoot(),
                change.repositoryIdentityHash(), identity,
                List.of(new StagedChangeFile(Path.of("z.java"), StagedFileStatus.ADDED, 1, 0),
                        new StagedChangeFile(Path.of("a.java"), StagedFileStatus.ADDED, 1, 0)), 2, 0));
    }
}
