package dev.codedefense.passport;

import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.Readiness;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.domain.TurnType;
import dev.codedefense.domain.Verdict;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class PassportTestFixtures {
    private PassportTestFixtures() { }
    public static ChangePassport passport(PassportStatus status) {
        StagedChange change = new StagedChange(Path.of("C:/safe/project").toAbsolutePath().normalize(), "a".repeat(64), "b".repeat(40), "c".repeat(64), "d".repeat(64),
                List.of(new StagedChangeFile(Path.of("src/App.java"), StagedFileStatus.MODIFIED, 2, 1)), 2, 1);
        List<TechnicalQuestion> questions = List.of(question("decision"), question("counterfactual"), question("test-prediction"));
        ProjectAnalysis analysis = new ProjectAnalysis("demo", "Java", "summary", List.of("a", "b"), List.of(new dev.codedefense.domain.ProjectComponent("App", "role", "summary", List.of("src/App.java"))), List.of("one", "two"), questions);
        List<QuestionResult> results = List.of(result(1, questions.get(0), 74), result(2, questions.get(1), 90), result(3, questions.get(2), 0));
        InterviewSession session = new InterviewSession("demo", results, 55, Readiness.REVIEW_NEEDED, 0);
        return new ChangePassport(change, analysis, session, Instant.parse("2026-07-18T00:00:00Z"), "model", status);
    }
    private static TechnicalQuestion question(String id) { return new TechnicalQuestion(id, "prompt", "goal", List.of("expected-key-point", "two"), List.of(new CodeEvidence("src/App.java", 1, 2, "evidence-reason"))); }
    private static QuestionResult result(int number, TechnicalQuestion question, int score) { return new QuestionResult(number, question, new InterviewTurn(TurnType.PRIMARY, "prompt", "private-answer", new AnswerEvaluation(Verdict.PARTIAL, score, "feedback", List.of("understood"), List.of("missing"), Optional.empty())), Optional.empty(), score); }
}
