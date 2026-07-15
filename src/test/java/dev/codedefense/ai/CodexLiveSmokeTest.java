package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * An explicitly opt-in live Codex smoke test. It is disabled for the normal Maven test suite.
 */
@EnabledIfSystemProperty(named = "codedefense.live.codex", matches = "true")
class CodexLiveSmokeTest {
    private static final String SCHEMA_RESOURCE = "/schemas/codex-live-smoke.schema.json";
    private static final int MAXIMUM_SCHEMA_RESOURCE_BYTES = 8 * 1024;
    private static final String PROMPT = """
            Return the JSON object required by the supplied schema.
            Set status to \"ok\".
            Use a short message confirming that structured Codex execution works.
            Do not run commands or use tools.
            """;

    @Test
    void executesAConstrainedStructuredCodexRequest() throws IOException {
        CodexRuntimeConfig config = CodexRuntimeConfig.defaults();
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<Path> workspacePath = new AtomicReference<>();
        CodexEnvironmentChecker preflight = CodexEnvironmentChecker.forCurrentEnvironment(
                new JdkProcessExecutor(),
                config,
                new CodexProcessEnvironment(),
                Path.of(".").toAbsolutePath().normalize());
        CodexProcessRunner runner = new CodexProcessRunner(
                new JdkProcessExecutor(),
                new CodexCommandFactory(),
                new CodexProcessEnvironment(),
                config,
                objectMapper,
                () -> {
                    CodexTemporaryWorkspace workspace = CodexTemporaryWorkspace.create();
                    workspacePath.set(workspace.workspace());
                    return workspace;
                },
                System.getenv());
        StructuredCodexRequest request = new StructuredCodexRequest(
                "live-smoke",
                PROMPT,
                schemaJson(),
                config.defaultModel(),
                ReasoningEffort.LOW,
                Duration.ofSeconds(120));

        StructuredCodexResult result = runner.execute(preflight.checkReady(), request);
        JsonNode response = objectMapper.readTree(result.finalJson());

        assertEquals("ok", response.path("status").asText());
        assertTrue(response.path("message").isTextual());
        assertTrue(!response.path("message").asText().isBlank());
        assertNotNull(workspacePath.get());
        assertFalse(Files.exists(workspacePath.get(), LinkOption.NOFOLLOW_LINKS));
        System.out.println("Live smoke JSON status: " + response.path("status").asText());
    }

    private static String schemaJson() throws IOException {
        try (InputStream input = CodexLiveSmokeTest.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IOException("Live smoke schema resource is missing.");
            }
            byte[] schemaBytes = input.readNBytes(MAXIMUM_SCHEMA_RESOURCE_BYTES);
            if (input.read() != -1) {
                throw new IOException("Live smoke schema resource exceeds its maximum size.");
            }
            return new String(schemaBytes, StandardCharsets.UTF_8);
        }
    }
}
