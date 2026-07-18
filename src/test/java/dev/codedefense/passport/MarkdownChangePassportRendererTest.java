package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.PassportStatus;
import java.time.Instant;
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
}
