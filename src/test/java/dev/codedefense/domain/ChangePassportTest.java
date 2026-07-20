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
        ChangePassport passport = new ChangePassport(change(), analysis, session("project-a", analysis), Instant.EPOCH,
                "model", PassportStatus.CURRENT);
        Path passportPath = absolutePath("passport.json");
        PassportVerification verification = new PassportVerification(passportPath, PassportStatus.CURRENT);

        assertEquals(PassportStatus.CURRENT, passport.statusAtCreation());
        assertEquals(passportPath, verification.passport());
        for (String sentinel : List.of("private-answer", "private-prompt", "private-feedback", "private-key-point", "private-evidence-reason")) {
            assertFalse(passport.toString().contains(sentinel));
        }
    }

    @Test
    void rejectsNullAnalysisAndSessionForAnotherProject() {
        ProjectAnalysis analysis = analysis("project-a");
        assertThrows(NullPointerException.class, () -> new ChangePassport(change(), null, session("project-a", analysis),
                Instant.EPOCH, "model", PassportStatus.CURRENT));
        assertThrows(IllegalArgumentException.class, () -> new ChangePassport(change(), analysis, session("project-b", analysis),
                Instant.EPOCH, "model", PassportStatus.CURRENT));
    }

    @Test
    void rejectsSessionResultsThatDoNotMatchAnalysisQuestionIdsInOrder() {
        ProjectAnalysis analysis = analysis("project-a");
        List<QuestionResult> reordered = List.of(
                result(1, analysis.questions().get(1)),
                result(2, analysis.questions().get(0)),
                result(3, analysis.questions().get(2)));
        InterviewSession session = new InterviewSession("project-a", reordered, 80,
                Readiness.STRONG_UNDERSTANDING, 0);

        assertThrows(IllegalArgumentException.class, () -> new ChangePassport(change(), analysis, session,
                Instant.EPOCH, "model", PassportStatus.CURRENT));
    }

    private static StagedChange change() {
        return new StagedChange(absolutePath("repository"), "a".repeat(64), "b".repeat(40), "b".repeat(64),
                "c".repeat(64), List.of(new StagedChangeFile(Path.of("src/App.java"), StagedFileStatus.ADDED, 1, 0)), 1, 0);
    }

    private static Path absolutePath(String name) {
        return Path.of(System.getProperty("java.io.tmpdir"), "codedefense-tests", name).toAbsolutePath().normalize();
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

    private static InterviewSession session(String projectName, ProjectAnalysis analysis) {
        return new InterviewSession(projectName, List.of(result(1, analysis.questions().get(0)),
                result(2, analysis.questions().get(1)), result(3, analysis.questions().get(2))), 80,
                Readiness.STRONG_UNDERSTANDING, 0);
    }

    private static QuestionResult result(int number, TechnicalQuestion question) {
        AnswerEvaluation evaluation = new AnswerEvaluation(Verdict.CORRECT, 80, "private-feedback", List.of("understood"),
                List.of(), Optional.empty());
        InterviewTurn turn = new InterviewTurn(TurnType.PRIMARY, "private-prompt", "private-answer", evaluation);
        return new QuestionResult(number, question, turn, Optional.empty(), 80);
    }
}
