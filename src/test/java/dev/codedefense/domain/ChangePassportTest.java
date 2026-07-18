package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangePassportTest {
    @Test
    void constructsPassportAndVerificationWithoutLeakingPrivateInterviewData() {
        ProjectAnalysis analysis = analysis("project-a");
        ChangePassport passport = new ChangePassport(change(), analysis, session("project-a"), Instant.EPOCH,
                "model", PassportStatus.CURRENT);
        PassportVerification verification = new PassportVerification(Path.of("C:/passport.json"), PassportStatus.CURRENT);

        assertEquals(PassportStatus.CURRENT, passport.statusAtCreation());
        assertEquals(Path.of("C:/passport.json"), verification.passport());
        for (String sentinel : List.of("private-answer", "private-prompt", "private-feedback", "private-key-point", "private-evidence-reason")) {
            assertFalse(passport.toString().contains(sentinel));
        }
    }

    @Test
    void rejectsNullAnalysisAndSessionForAnotherProject() {
        assertThrows(NullPointerException.class, () -> new ChangePassport(change(), null, session("project-a"),
                Instant.EPOCH, "model", PassportStatus.CURRENT));
        assertThrows(IllegalArgumentException.class, () -> new ChangePassport(change(), analysis("project-a"), session("project-b"),
                Instant.EPOCH, "model", PassportStatus.CURRENT));
    }

    private static StagedChange change() {
        return new StagedChange(Path.of("C:/repository"), "a".repeat(64), "b".repeat(40), "b".repeat(40),
                "c".repeat(64), List.of(new StagedChangeFile(Path.of("src/App.java"), StagedFileStatus.ADDED, 1, 0)), 1, 0);
    }

    private static ProjectAnalysis analysis(String projectName) {
        return new ProjectAnalysis(projectName, "Java", "summary", List.of("first", "second"),
                List.of(new ProjectComponent("component", "kind", "responsibility", List.of("src/App.java"))),
                List.of("topic-one", "topic-two"), List.of(question("one"), question("two"), question("three")));
    }

    private static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(id, "private-prompt", "goal", List.of("private-key-point", "other"),
                List.of(new CodeEvidence("src/App.java", 1, 1, "private-evidence-reason")));
    }

    private static InterviewSession session(String projectName) {
        return new InterviewSession(projectName, List.of(result(1), result(2), result(3)), 80,
                Readiness.STRONG_UNDERSTANDING, 0);
    }

    private static QuestionResult result(int number) {
        AnswerEvaluation evaluation = new AnswerEvaluation(Verdict.CORRECT, 80, "private-feedback", List.of("understood"),
                List.of(), Optional.empty());
        InterviewTurn turn = new InterviewTurn(TurnType.PRIMARY, "private-prompt", "private-answer", evaluation);
        return new QuestionResult(number, question("question-" + number), turn, Optional.empty(), 80);
    }
}
