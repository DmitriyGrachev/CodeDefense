package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.Readiness;
import dev.codedefense.domain.TechnicalQuestion;
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
        assertTrue(markdown.contains("Experimental Codex provenance: Not requested"));
        assertTrue(markdown.contains("Java-owned final score"));
        assertTrue(markdown.contains("src/App\\.java"));
        assertFalse(markdown.contains("private-staged-source"));
        assertFalse(markdown.contains("private-answer"));
        assertFalse(markdown.contains(passport.change().repositoryRoot().toString()));
        assertFalse(markdown.contains("expected-key-point"));
        assertFalse(markdown.contains("evidence-reason"));
    }

    @Test
    void rendersQuestionCategoriesAndOnlyStructuredAssessmentFacts() {
        ChangePassport passport = PassportTestFixtures.passport(PassportStatus.CURRENT);

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertTrue(markdown.indexOf("## Decision defense") < markdown.indexOf("## Counterfactual defense"));
        assertTrue(markdown.indexOf("## Counterfactual defense") < markdown.indexOf("## Test prediction"));
        assertTrue(markdown.contains("- Evidence: src/App\\.java:1-2"));
        assertTrue(markdown.contains("### Primary evaluation"));
        assertTrue(markdown.contains("- Verdict: PARTIAL"));
        assertTrue(markdown.contains("- Score: 74/100"));
        assertTrue(markdown.contains("- Local final score: 0/100"));
        assertFalse(markdown.contains("- Question:"));
        assertFalse(markdown.contains("- Feedback:"));
        assertFalse(markdown.contains("- Understood concepts:"));
        assertFalse(markdown.contains("- Knowledge gaps:"));
        assertFalse(markdown.contains("private-answer"));
        assertFalse(markdown.contains("expected-key-point"));
        assertFalse(markdown.contains("evidence-reason"));
        assertFalse(markdown.contains("private-staged-source"));
        assertFalse(markdown.contains("raw JSON"));
    }

    @Test
    void rendersStructuredFollowUpEvaluationWithoutModelProseOrEitherAnswer() {
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
        assertTrue(markdown.contains("- Verdict: CORRECT"));
        assertTrue(markdown.contains("- Score: 80/100"));
        assertTrue(markdown.contains("- Local final score: 61/100"));
        assertFalse(markdown.contains("follow-up question"));
        assertFalse(markdown.contains("follow-up feedback"));
        assertFalse(markdown.contains("follow-up understood"));
        assertFalse(markdown.contains("follow-up missing"));
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

    @Test
    void omitsAllModelControlledProseThatCanRepeatStagedSource() {
        ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        String stagedLiteral = "STAGED_HUNK_VALUE";
        TechnicalQuestion question = new TechnicalQuestion("decision",
                "Why return " + stagedLiteral + "?", "goal", List.of("one", "two"),
                List.of(new CodeEvidence("src/App.java", 2, 3, "private evidence reason")));
        InterviewTurn primary = new InterviewTurn(TurnType.PRIMARY, question.prompt(), "private answer",
                new AnswerEvaluation(Verdict.INCORRECT, 20, "Feedback quotes " + stagedLiteral,
                        List.of("Understood " + stagedLiteral), List.of("Missing " + stagedLiteral),
                        Optional.of("Follow up about " + stagedLiteral)));
        InterviewTurn followUp = new InterviewTurn(TurnType.FOLLOW_UP, "Follow up about " + stagedLiteral,
                "private follow-up answer", new AnswerEvaluation(Verdict.CORRECT, 95,
                        "Follow-up quotes " + stagedLiteral, List.of("Understood " + stagedLiteral),
                        List.of(), Optional.empty()));
        QuestionResult first = new QuestionResult(1, question, primary, Optional.of(followUp), 65);
        InterviewSession session = new InterviewSession(original.session().projectName(), List.of(first,
                original.session().results().get(1), original.session().results().get(2)), 55,
                Readiness.REVIEW_NEEDED, 0);
        ChangePassport passport = new ChangePassport(original.change(), original.analysis(), session,
                original.createdAt(), original.model(), original.statusAtCreation());

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertFalse(markdown.contains(stagedLiteral));
        assertFalse(markdown.contains("STAGED\\_HUNK\\_VALUE"));
        assertFalse(markdown.contains("private answer"));
        assertFalse(markdown.contains("private follow-up answer"));
        assertTrue(markdown.contains("### Primary evaluation"));
        assertTrue(markdown.contains("### Follow-up evaluation"));
        assertTrue(markdown.contains("- Verdict: INCORRECT"));
        assertTrue(markdown.contains("- Score: 20/100"));
        assertTrue(markdown.contains("- Verdict: CORRECT"));
        assertTrue(markdown.contains("- Score: 95/100"));
        assertTrue(markdown.contains("- Evidence: src/App\\.java:2-3"));
        assertTrue(markdown.contains("- Local final score: 65/100"));
    }

    @Test
    void privacyStatementDoesNotRepeatForbiddenAcceptanceMarkers() {
        String markdown = new MarkdownChangePassportRenderer()
                .render(PassportTestFixtures.passport(PassportStatus.CURRENT));
        String lowerCase = markdown.toLowerCase(java.util.Locale.ROOT);

        assertFalse(lowerCase.contains("expected key points"));
        assertFalse(lowerCase.contains("evidence reasons"));
        assertFalse(lowerCase.contains("raw model json"));
    }

    @Test
    void rendersOnlySourceFreeProvenanceSummaryAndHonestyBoundary() {
        ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        var provenance = new dev.codedefense.domain.CodexProvenanceSummary(1,
                dev.codedefense.domain.CodexProvenanceStatus.EXACT_CHANGE_MATCH,
                "e".repeat(64), "0.144.0", 1, 1, List.of("src/App.java"),
                Instant.parse("2026-07-19T12:00:00Z"));
        ChangePassport passport = new ChangePassport(original.change(), original.changeKind(),
                original.sourceIdentity(), original.analysis(), original.session(), original.createdAt(),
                original.model(), original.statusAtCreation(), original.focus(), Optional.of(provenance));

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertTrue(markdown.contains("Experimental Codex provenance: Exact change match"));
        assertTrue(markdown.contains("Matched files: 1/1"));
        assertTrue(markdown.contains("e".repeat(64)));
        assertTrue(markdown.contains("does not prove authorship"));
        for (String forbidden : new String[] {"private-thread-id", "PRIVATE-TRANSCRIPT",
                "@@ -1 +1 @@", "private-answer"}) assertFalse(markdown.contains(forbidden));
    }

}
