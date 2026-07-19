package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.Readiness;
import dev.codedefense.domain.TurnType;
import dev.codedefense.domain.Verdict;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MarkdownChangePassportRendererTest {
    @Test
    void rendersSourceFreePassportSections() {
        ChangePassport passport = PassportTestFixtures.passport(PassportStatus.CURRENT);

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertTrue(markdown.contains("# CodeDefense Change Passport"));
        for (String heading : new String[] {"Change identity", "Status", "Local assessment", "Changed files",
                "Decision defense", "Counterfactual defense", "Test prediction", "Privacy"}) {
            assertTrue(markdown.contains("## " + heading));
        }
        assertTrue(markdown.contains("Codex session link: NOT_REQUESTED"));
        assertTrue(markdown.contains("Java-owned final score"));
        assertTrue(markdown.contains("src/App\\.java"));
        assertFalse(markdown.contains("private-staged-source"));
        assertFalse(markdown.contains("private-answer"));
        assertFalse(markdown.contains(passport.change().repositoryRoot().toString()));
        assertFalse(markdown.contains("expected-key-point"));
        assertFalse(markdown.contains("evidence-reason"));
    }

    @Test
    void rendersQuestionCategoriesAndOnlySafeEducationalContext() {
        ChangePassport passport = PassportTestFixtures.passport(PassportStatus.CURRENT);

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertTrue(markdown.indexOf("## Decision defense") < markdown.indexOf("## Counterfactual defense"));
        assertTrue(markdown.indexOf("## Counterfactual defense") < markdown.indexOf("## Test prediction"));
        assertTrue(markdown.contains("- Question: prompt"));
        assertTrue(markdown.contains("- Evidence: src/App\\.java:1-2"));
        assertTrue(markdown.contains("- Understood concepts: understood"));
        assertTrue(markdown.contains("- Knowledge gaps: missing"));
        assertTrue(markdown.contains("- Local final score: 0/100"));
        assertFalse(markdown.contains("private-answer"));
        assertFalse(markdown.contains("expected-key-point"));
        assertFalse(markdown.contains("evidence-reason"));
        assertFalse(markdown.contains("private-staged-source"));
        assertFalse(markdown.contains("raw JSON"));
    }

    @Test
    void rendersSafeFollowUpEvaluationWithoutEitherAnswer() {
        ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        QuestionResult primary = original.session().results().getFirst();
        InterviewTurn followUp = new InterviewTurn(TurnType.FOLLOW_UP, "follow-up question",
                "private-follow-up-answer", new AnswerEvaluation(Verdict.CORRECT, 80, "follow-up feedback",
                        List.of("follow-up understood"), List.of("follow-up missing"), Optional.empty()));
        QuestionResult withFollowUp = new QuestionResult(1, primary.question(), primary.primaryTurn(),
                Optional.of(followUp), 61);
        InterviewSession session = new InterviewSession(original.session().projectName(), List.of(withFollowUp,
                original.session().results().get(1), original.session().results().get(2)), 55,
                Readiness.REVIEW_NEEDED, 0);
        ChangePassport passport = new ChangePassport(original.change(), original.analysis(), session,
                Instant.parse("2026-07-18T00:00:00Z"), "model", PassportStatus.CURRENT);

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertTrue(markdown.contains("### Primary evaluation"));
        assertTrue(markdown.contains("### Follow-up evaluation"));
        assertTrue(markdown.contains("- Question: follow\\-up question"));
        assertTrue(markdown.contains("- Verdict: CORRECT"));
        assertTrue(markdown.contains("- Score: 80/100"));
        assertTrue(markdown.contains("- Feedback: follow\\-up feedback"));
        assertTrue(markdown.contains("- Understood concepts: follow\\-up understood"));
        assertTrue(markdown.contains("- Knowledge gaps: follow\\-up missing"));
        assertTrue(markdown.contains("- Local final score: 61/100"));
        assertFalse(markdown.contains("private-answer"));
        assertFalse(markdown.contains("private-follow-up-answer"));
    }

    @Test
    void rendersBothPathsForAnExactRename() {
        ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        var renamed = new dev.codedefense.domain.StagedChangeFile(Path.of("src/NewName.java"),
                Optional.of(Path.of("src/OldName.java")), dev.codedefense.domain.StagedFileStatus.RENAMED, 0, 0);
        var change = new dev.codedefense.domain.StagedChange(original.change().repositoryRoot(),
                original.change().repositoryIdentityHash(), original.change().baseCommit(),
                original.change().indexIdentity(), original.change().diffFingerprint(), List.of(renamed), 0, 0);
        ChangePassport passport = new ChangePassport(change, original.analysis(), original.session(),
                original.createdAt(), original.model(), original.statusAtCreation());

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertTrue(markdown.contains("src/OldName\\.java → src/NewName\\.java"));
        assertFalse(markdown.contains("private-staged-source"));
    }

}
