package dev.codedefense.change;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.EvidenceCoverageState;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.GitChangeIdentity;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.SourceLineRange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.domain.TechnicalQuestion;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceCoverageCalculatorTest {
    @Test
    void countsOnlyChangedLinesAndUsesDeletionAnchor() {
        StagedChangeFile modified = new StagedChangeFile(
                Path.of("src/App.java"), StagedFileStatus.MODIFIED, 2, 1);
        StagedChangeFile deleted = new StagedChangeFile(
                Path.of("src/Legacy.java"), StagedFileStatus.DELETED, 0, 2);
        GitChange change = new GitChange(Path.of(".").toAbsolutePath().normalize(), "a".repeat(64),
                new GitChangeIdentity(ChangeKind.STAGED, "b".repeat(40), "c".repeat(64), "d".repeat(64)),
                List.of(modified, deleted), 2, 3);
        List<StagedHunk> hunks = List.of(
                new StagedHunk(modified, 9, 3, 9, 4, " context\n-old\n+changed\n context",
                        false, List.of(new SourceLineRange(10, 10))),
                new StagedHunk(modified, 29, 2, 30, 3, " context\n+other\n context",
                        false, List.of(new SourceLineRange(31, 31))),
                new StagedHunk(deleted, 40, 2, 40, 0, "-gone\n-goneToo",
                        false, List.of()));

        var coverage = new EvidenceCoverageCalculator().calculate(
                new CapturedGitChange(change, hunks), analysis());

        assertEquals(3, coverage.summary().totalHunks());
        assertEquals(3, coverage.summary().measurableHunks());
        assertEquals(2, coverage.summary().referencedHunks());
        assertEquals(67, coverage.summary().percentage().orElseThrow());
        assertEquals(EvidenceCoverageState.REFERENCED, coverage.hunks().get(0).state());
        assertEquals(EvidenceCoverageState.UNREFERENCED, coverage.hunks().get(1).state());
        assertEquals(EvidenceCoverageState.REFERENCED, coverage.hunks().get(2).state());
        assertEquals(40, coverage.hunks().get(2).startLine());
        assertEquals(false, coverage.hunks().get(2).navigable());
    }

    private static ProjectAnalysis analysis() {
        return new ProjectAnalysis("fixture", "Java", "summary", List.of("one", "two"),
                List.of(new ProjectComponent("core", "class", "logic", List.of("src/App.java"))),
                List.of("decision", "tests"), List.of(
                        question("decision", new CodeEvidence("src/App.java", 10, 10, "changed line")),
                        question("counterfactual", new CodeEvidence("src/App.java", 9, 9, "context only")),
                        question("test-prediction", new CodeEvidence("src/Legacy.java", 40, 41, "deletion"))));
    }

    private static TechnicalQuestion question(String id, CodeEvidence evidence) {
        return new TechnicalQuestion(id, "Question " + id, "goal",
                List.of("point one", "point two"), List.of(evidence));
    }
}
