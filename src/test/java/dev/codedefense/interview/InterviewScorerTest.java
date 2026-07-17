package dev.codedefense.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.domain.TurnType;
import dev.codedefense.domain.Verdict;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InterviewScorerTest {
    private final InterviewScorer scorer = new InterviewScorer();

    @Test
    void calculatesQuestionScoresLocally() {
        assertEquals(50, scorer.calculateQuestionScore(turn(TurnType.PRIMARY, 50), Optional.empty()));
        assertEquals(74, scorer.calculateQuestionScore(
                turn(TurnType.PRIMARY, 50), Optional.of(turn(TurnType.FOLLOW_UP, 90))));
        assertEquals(50, scorer.calculateQuestionScore(
                turn(TurnType.PRIMARY, 50), Optional.of(turn(TurnType.FOLLOW_UP, 20))));
        assertEquals(50, scorer.calculateQuestionScore(
                turn(TurnType.PRIMARY, 50), Optional.of(skippedFollowUp())));
        assertEquals(0, scorer.calculateQuestionScore(skippedPrimary(), Optional.empty()));
    }

    @Test
    void roundsTheArithmeticMeanOfExactlyThreeQuestionScores() {
        assertEquals(55, scorer.calculateOverallScore(List.of(result(1, 54), result(2, 55), result(3, 55))));
        assertThrows(IllegalArgumentException.class, () -> scorer.calculateOverallScore(List.of(result(1, 50))));
    }

    @Test
    void interviewConfigDefinesTheOnlyMvpLimits() {
        InterviewConfig config = InterviewConfig.defaults();

        assertEquals(3, config.primaryQuestionCount());
        assertEquals(1, config.maximumFollowUpsPerQuestion());
        assertEquals(8_000, config.maximumAnswerCharacters());
        assertEquals(Duration.ofSeconds(120), config.evaluationTimeout());
        assertThrows(IllegalArgumentException.class, () -> new InterviewConfig(2, 1, 8_000, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new InterviewConfig(3, 2, 8_000, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new InterviewConfig(3, 1, 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new InterviewConfig(3, 1, 8_000, Duration.ZERO));
    }

    private static QuestionResult result(int number, int score) {
        return new QuestionResult(number, question(number), turn(TurnType.PRIMARY, score), Optional.empty(), score);
    }

    private static InterviewTurn turn(TurnType type, int score) {
        return new InterviewTurn(type, "Prompt", "Answer",
                new AnswerEvaluation(Verdict.PARTIAL, score, "Feedback", List.of(), List.of(), Optional.empty()));
    }

    private static InterviewTurn skippedPrimary() {
        return new InterviewTurn(TurnType.PRIMARY, "Prompt", "skip", AnswerEvaluation.skipped());
    }

    private static InterviewTurn skippedFollowUp() {
        return new InterviewTurn(TurnType.FOLLOW_UP, "Prompt", "skip", AnswerEvaluation.skipped());
    }

    private static TechnicalQuestion question(int number) {
        return new TechnicalQuestion("q" + number, "Question prompt " + number, "Learning goal",
                List.of("Point one", "Point two"),
                List.of(new CodeEvidence("src/App.java", 1, 2, "Reason")));
    }
}
