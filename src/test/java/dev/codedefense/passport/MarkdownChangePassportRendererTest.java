package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.Readiness;
import java.time.Instant;
import java.util.List;
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
    void labelsQuestionsByTheirActualCategoryAndRendersOnlySafeEducationalContext() {
        ChangePassport original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        List<QuestionResult> originalResults = original.session().results();
        List<QuestionResult> reordered = List.of(
                renumber(1, originalResults.get(2)), renumber(2, originalResults.get(0)), renumber(3, originalResults.get(1)));
        InterviewSession session = new InterviewSession(original.session().projectName(), reordered, 55, Readiness.REVIEW_NEEDED, 0);
        ChangePassport passport = new ChangePassport(original.change(), original.analysis(), session,
                Instant.parse("2026-07-18T00:00:00Z"), "model", PassportStatus.CURRENT);

        String markdown = new MarkdownChangePassportRenderer().render(passport);

        assertTrue(markdown.indexOf("## Test prediction") < markdown.indexOf("## Decision defense"));
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

    private static QuestionResult renumber(int number, QuestionResult source) {
        return new QuestionResult(number, source.question(), source.primaryTurn(), source.followUpTurn(), source.finalScore());
    }
}
