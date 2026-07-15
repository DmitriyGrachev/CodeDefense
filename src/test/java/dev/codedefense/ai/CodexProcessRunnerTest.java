package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexProcessRunnerTest {
    @TempDir
    Path temporaryParent;

    @Test
    void sendsPromptOnlyThroughStdinWritesSchemaAndCleansWorkspaceAfterSuccess() {
        AtomicBoolean schemaPresent = new AtomicBoolean();
        FakeProcessExecutor executor = new FakeProcessExecutor(specification -> {
            schemaPresent.set(Files.exists(pathAfter(specification.command(), "--output-schema")));
            writeOutput(specification, "{\"status\":\"ok\"}\r\n \t");
            return result(0, "", "", false);
        });
        StructuredCodexRequest request = request("private prompt", "{\"type\":\"object\"}");

        StructuredCodexResult result = runner(executor).execute(environment(), request);

        assertEquals("{\"status\":\"ok\"}\r\n \t", result.finalJson());
        assertEquals(request.model(), result.model());
        assertEquals(request.prompt(), executor.specification.standardInput());
        assertTrue(schemaPresent.get());
        assertEquals("kept", executor.specification.environment().get("ORDINARY"));
        assertFalse(executor.specification.environment().containsKey("OPENAI_API_KEY"));
        assertEquals(new CodexCommandFactory().create(
                environment().executable(),
                request,
                executor.workspace,
                executor.workspace.resolve("schema.json"),
                executor.workspace.resolve("final-message.json")), executor.specification.command());
        assertFalse(executor.specification.command().contains(request.prompt()));
        assertFalse(executor.specification.command().contains(request.schemaJson()));
        assertFalse(Files.exists(executor.workspace));
        assertParentIsEmpty();
    }

    @Test
    void mapsTimeoutAndNonzeroExitWithoutExposingCapturedDiagnostics() {
        FakeProcessExecutor timeoutExecutor = new FakeProcessExecutor(specification ->
                new ProcessResult(-1, "", "", false, false, true, Duration.ZERO));
        assertThrows(CodexTimeoutException.class, () -> runner(timeoutExecutor).execute(environment(), request("prompt", "{}")));
        assertParentIsEmpty();

        String diagnostic = "failure ".repeat(10_000);
        FakeProcessExecutor failingExecutor = new FakeProcessExecutor(specification -> result(17, "", diagnostic, true));
        CodexExecutionException exception = assertThrows(
                CodexExecutionException.class, () -> runner(failingExecutor).execute(environment(), request("private prompt", "{}")));

        assertEquals(17, exception.exitCode());
        assertTrue(exception.getMessage().contains("Captured diagnostics were omitted"));
        assertTrue(exception.getMessage().contains("had been truncated"));
        assertFalse(exception.getMessage().contains("private prompt"));
        assertFalse(exception.getMessage().contains(diagnostic.substring(0, 100)));
        assertParentIsEmpty();
    }

    @Test
    void omitsFullAndPartialPromptEchoesSchemaPathsAndTerminalControlsFromDiagnostics() {
        String longPrompt = "PRIVATE-SOURCE-" + "x".repeat(100_000);
        String schema = "{\"schema\":\"PRIVATE-SCHEMA\"}";
        StructuredCodexRequest request = request(longPrompt, schema);
        String partialEcho = longPrompt.substring(0, 4_000);
        FakeProcessExecutor fullEchoExecutor = new FakeProcessExecutor(specification -> {
            Path workspace = specification.workingDirectory();
            Path schemaPath = pathAfter(specification.command(), "--output-schema");
            Path outputPath = pathAfter(specification.command(), "--output-last-message");
            String stderr = "prompt=" + request.prompt()
                    + " schema=" + request.schemaJson()
                    + " workspace=" + workspace
                    + " schemaPath=" + schemaPath
                    + " outputPath=" + outputPath
                    + " home=" + System.getProperty("user.home")
                    + "\u001B[31m terminal\u0007";
            return result(9, "", stderr, false);
        });

        CodexExecutionException fullEchoException = assertThrows(
                CodexExecutionException.class, () -> runner(fullEchoExecutor).execute(environment(), request));
        FakeProcessExecutor partialEchoExecutor = new FakeProcessExecutor(
                specification -> result(9, "", partialEcho, true));
        CodexExecutionException partialEchoException = assertThrows(
                CodexExecutionException.class, () -> runner(partialEchoExecutor).execute(environment(), request));

        String message = fullEchoException.getMessage();
        assertFalse(message.contains(longPrompt.substring(0, 100)));
        assertFalse(message.contains(schema.substring(0, 20)));
        assertFalse(message.contains(fullEchoExecutor.workspace.toString()));
        assertFalse(message.contains(fullEchoExecutor.workspace.resolve("schema.json").toString()));
        assertFalse(message.contains(fullEchoExecutor.workspace.resolve("final-message.json").toString()));
        assertFalse(message.contains("codedefense-codex-"));
        assertFalse(message.contains(System.getProperty("user.home")));
        assertFalse(message.contains("\u001B"));
        assertFalse(message.contains("\u0007"));
        assertFalse(partialEchoException.getMessage().contains(partialEcho.substring(0, 100)));
        assertTrue(partialEchoException.getMessage().contains("had been truncated"));
        assertParentIsEmpty();
    }

    @Test
    void rejectsMissingEmptyMalformedScalarAndOversizedOutputAndCleansWorkspace() {
        assertInvalidOutput(specification -> result(0, "", "", false));
        assertInvalidOutput(specification -> {
            writeOutput(specification, "   ");
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutput(specification, "{");
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutput(specification, "[]");
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutput(specification, "42");
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutput(specification, "null");
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutput(specification, "{\"ok\":true} {\"second\":true}");
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutput(specification, "{\"ok\":true} trailing");
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutput(specification, " ".repeat(1024 * 1024 + 1));
            return result(0, "", "", false);
        });
        assertInvalidOutput(specification -> {
            writeOutputBytes(specification, new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xC3, 0x28, '"', '}'});
            return result(0, "", "", false);
        });
    }

    @Test
    void rejectsInvalidOrOversizedInputsWithoutLeakingThemOrCreatingWorkspace() {
        String secretPrompt = "prompt-secret";
        String secretSchema = "schema-secret";

        InvalidCodexResponseException schemaException = assertThrows(
                InvalidCodexResponseException.class,
                () -> runner(new FakeProcessExecutor(specification -> result(0, "", "", false)))
                        .execute(environment(), request(secretPrompt, "not-json")));
        assertFalse(schemaException.getMessage().contains(secretPrompt));
        assertFalse(schemaException.getMessage().contains("not-json"));

        assertThrows(
                InvalidCodexResponseException.class,
                () -> runner(new FakeProcessExecutor(specification -> result(0, "", "", false)))
                        .execute(environment(), request(secretPrompt, "[]")));
        assertThrows(
                InvalidCodexResponseException.class,
                () -> runner(new FakeProcessExecutor(specification -> result(0, "", "", false)))
                        .execute(environment(), request(secretPrompt, "null")));
        assertThrows(
                InvalidCodexResponseException.class,
                () -> runner(new FakeProcessExecutor(specification -> result(0, "", "", false)))
                        .execute(environment(), request(secretPrompt, "{} {}")));
        assertThrows(
                InvalidCodexResponseException.class,
                () -> runner(new FakeProcessExecutor(specification -> result(0, "", "", false)))
                        .execute(environment(), request(secretPrompt, "{} trailing")));

        InvalidCodexResponseException promptException = assertThrows(
                InvalidCodexResponseException.class,
                () -> runner(new FakeProcessExecutor(specification -> result(0, "", "", false)))
                        .execute(environment(), request("x".repeat(512 * 1024 + 1), "{}")));
        assertFalse(promptException.getMessage().contains("x".repeat(32)));

        InvalidCodexResponseException schemaLimitException = assertThrows(
                InvalidCodexResponseException.class,
                () -> runner(new FakeProcessExecutor(specification -> result(0, "", "", false)))
                        .execute(environment(), request(secretPrompt, "x".repeat(256 * 1024 + 1))));
        assertFalse(schemaLimitException.getMessage().contains(secretPrompt));
        assertFalse(Files.exists(temporaryParent.resolve("codedefense-codex-")));
    }

    @Test
    void mapsWorkspaceCreationAndSchemaWriteFailuresToExecutionFailures() {
        CodexExecutionException creationFailure = assertThrows(
                CodexExecutionException.class,
                () -> runner(
                        new FakeProcessExecutor(specification -> result(0, "", "", false)),
                        () -> {
                            throw new IOException("workspace failure");
                        }).execute(environment(), request("private prompt", "{}")));
        assertFalse(creationFailure.getMessage().contains("private prompt"));

        CodexExecutionException schemaFailure = assertThrows(
                CodexExecutionException.class,
                () -> runner(
                        new FakeProcessExecutor(specification -> result(0, "", "", false)),
                        () -> CodexTemporaryWorkspace.create(
                                temporaryParent,
                                CodexTemporaryWorkspace::deleteWorkspace,
                                (path, schema) -> {
                                    throw new IOException("schema failure");
                                })).execute(environment(), request("private prompt", "{}")));
        assertFalse(schemaFailure.getMessage().contains("private prompt"));
        assertParentIsEmpty();
    }

    @Test
    void runnerRetriesTransientWorkspaceCleanupBeforeReturning() {
        AtomicInteger deletionAttempts = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Path> workspacePath = new java.util.concurrent.atomic.AtomicReference<>();
        CodexTemporaryWorkspace.Factory workspaceFactory = () -> CodexTemporaryWorkspace.create(
                temporaryParent,
                path -> {
                    workspacePath.set(path);
                    if (deletionAttempts.getAndIncrement() == 0) {
                        throw new IOException("transient deletion failure");
                    }
                    CodexTemporaryWorkspace.deleteWorkspace(path);
                });
        FakeProcessExecutor executor = new FakeProcessExecutor(specification -> {
            writeOutput(specification, "{}");
            return result(0, "", "", false);
        });

        runner(executor, workspaceFactory).execute(environment(), request("prompt", "{}"));

        assertEquals(2, deletionAttempts.get());
        assertFalse(Files.exists(workspacePath.get()));
        assertParentIsEmpty();
    }

    private void assertInvalidOutput(Function<ProcessSpec, ProcessResult> action) {
        FakeProcessExecutor executor = new FakeProcessExecutor(action);
        InvalidCodexResponseException exception = assertThrows(
                InvalidCodexResponseException.class, () -> runner(executor).execute(environment(), request("private prompt", "{}")));
        assertFalse(exception.getMessage().contains("private prompt"));
        assertParentIsEmpty();
    }

    private CodexProcessRunner runner(ProcessExecutor executor) {
        return runner(executor, () -> CodexTemporaryWorkspace.create(temporaryParent));
    }

    private CodexProcessRunner runner(ProcessExecutor executor, CodexTemporaryWorkspace.Factory workspaceFactory) {
        return new CodexProcessRunner(
                executor,
                new CodexCommandFactory(),
                new CodexProcessEnvironment(),
                CodexRuntimeConfig.defaults(),
                new ObjectMapper(),
                workspaceFactory,
                Map.of("OPENAI_API_KEY", "secret", "ORDINARY", "kept"));
    }

    private static CodexEnvironment environment() {
        return new CodexEnvironment(new CodexExecutable(List.of("codex")), "codex 1");
    }

    private static StructuredCodexRequest request(String prompt, String schema) {
        return new StructuredCodexRequest(
                "operation", prompt, schema, "test-model", ReasoningEffort.LOW, Duration.ofSeconds(2));
    }

    private static ProcessResult result(int exitCode, String stdout, String stderr, boolean stderrTruncated) {
        return new ProcessResult(exitCode, stdout, stderr, false, stderrTruncated, false, Duration.ofMillis(12));
    }

    private static Path pathAfter(List<String> command, String option) {
        return Path.of(command.get(command.indexOf(option) + 1));
    }

    private static void writeOutput(ProcessSpec specification, String output) {
        try {
            Files.writeString(pathAfter(specification.command(), "--output-last-message"), output);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void writeOutputBytes(ProcessSpec specification, byte[] output) {
        try {
            Files.write(pathAfter(specification.command(), "--output-last-message"), output);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertParentIsEmpty() {
        try (var paths = Files.list(temporaryParent)) {
            assertEquals(List.of(), paths.toList());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeProcessExecutor implements ProcessExecutor {
        private final Function<ProcessSpec, ProcessResult> action;
        private ProcessSpec specification;
        private Path workspace;

        private FakeProcessExecutor(Function<ProcessSpec, ProcessResult> action) {
            this.action = action;
        }

        @Override
        public ProcessResult execute(ProcessSpec specification) {
            this.specification = specification;
            this.workspace = specification.workingDirectory();
            return action.apply(specification);
        }
    }
}
