package dev.codedefense.ci;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GitHubActionContractTest {
    @Test
    void actionIsReadOnlySourceFreeAndUsesFullHistoryExample() throws Exception {
        String action = Files.readString(Path.of(".github/actions/passport-check/action.yml"));
        String workflow = Files.readString(Path.of(".github/workflows/codedefense-passport.yml"));
        String combined = action + "\n" + workflow;

        assertTrue(action.contains("java-version: '21'"));
        assertTrue(action.contains("cache: maven"));
        assertTrue(action.contains("--format github"));
        assertTrue(action.contains("GITHUB_STEP_SUMMARY"));
        assertTrue(workflow.contains("fetch-depth: 0"));
        assertTrue(workflow.contains("contents: read"));
        assertTrue(workflow.contains("pull_request.base.sha"));
        assertTrue(workflow.contains("github.event.before"));
        assertFalse(combined.contains("contents: write"));
        assertFalse(combined.contains("OPENAI_API_KEY"));
        assertFalse(combined.contains("codex exec"));
    }
}
