package dev.codedefense.cli;

import dev.codedefense.application.StagedPassportGateEvaluator;
import dev.codedefense.codexhook.CodexHookStatusRenderer;
import dev.codedefense.domain.StagedPassportGateReason;
import dev.codedefense.domain.StagedPassportGateResult;
import dev.codedefense.domain.StagedPassportGateState;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexHookStatusCommandTest {
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void evaluatesOnlyTheCurrentWorkingDirectoryAndUsesConfiguredOutput() {
        AtomicReference<Path> evaluated = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(() -> repository -> {
            evaluated.set(repository);
            return current();
        }, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute());
        assertEquals(Path.of(".").toAbsolutePath().normalize(),
                evaluated.get().toAbsolutePath().normalize());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Passport aaaaaaaaaaaa"));
    }

    @Test
    void silentGateStateWritesNoStdout() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(() -> repository -> noStaged(),
                output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute());
        assertEquals(0, output.size());
    }

    @Test
    void unexpectedFailureUsesOneSafeConfiguredErrorAndDocumentedExitCode() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(() -> repository -> {
            throw new IllegalStateException("private-path-marker");
        }, new ByteArrayOutputStream(), error);

        assertEquals(ExitCodes.GIT_EXECUTION_FAILED, commandLine.execute());

        String message = error.toString(StandardCharsets.UTF_8);
        assertEquals("CodeDefense hook status is unavailable.", message.strip());
        assertFalse(message.contains("private-path-marker"));
        assertFalse(message.contains("IllegalStateException"));
        assertFalse(message.contains("\tat "));
    }

    @Test
    void helpAndVersionNeverConstructOrEvaluateTheGate() {
        AtomicInteger constructions = new AtomicInteger();
        Supplier<StagedPassportGateEvaluator> factory = () -> {
            constructions.incrementAndGet();
            throw new AssertionError("gate must stay lazy");
        };
        CommandLine commandLine = commandLine(factory,
                new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--help"));
        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--version"));
        assertEquals(0, constructions.get());
    }

    @Test
    void rejectsARepositoryArgumentWithoutEvaluating() {
        AtomicInteger calls = new AtomicInteger();
        CommandLine commandLine = commandLine(() -> repository -> {
            calls.incrementAndGet();
            return current();
        }, new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.INVALID_USAGE, commandLine.execute("private-repository-path"));
        assertEquals(0, calls.get());
    }

    private static CommandLine commandLine(Supplier<? extends StagedPassportGateEvaluator> factory,
            ByteArrayOutputStream output, ByteArrayOutputStream error) {
        CommandLine commandLine = new CommandLine(
                new CodexHookStatusCommand(factory, new CodexHookStatusRenderer()));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));
        commandLine.setParameterExceptionHandler((exception, arguments) -> ExitCodes.INVALID_USAGE);
        return commandLine;
    }

    private static StagedPassportGateResult noStaged() {
        return new StagedPassportGateResult(1, StagedPassportGateState.NO_STAGED_CHANGE,
                StagedPassportGateReason.NO_INDEX_ENTRIES, "", 0, 0, 0, 0, List.of());
    }

    private static StagedPassportGateResult current() {
        return new StagedPassportGateResult(1, StagedPassportGateState.CURRENT,
                StagedPassportGateReason.IDENTITY_MATCH, FINGERPRINT, 3, 2, 18, 4, List.of());
    }
}
