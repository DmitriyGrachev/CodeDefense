package dev.codedefense.cli;

import dev.codedefense.domain.CategoryLearningInsight;
import dev.codedefense.domain.RepositoryLearningInsights;
import dev.codedefense.application.RepositoryLearningInsightsException;
import dev.codedefense.passport.RepositoryLearningInsightsJsonCodec;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportInsightsCommandTest {
    @Test
    void defaultsToCurrentDirectoryAndTwentyAttempts() {
        AtomicReference<Path> requestedPath = new AtomicReference<>();
        AtomicInteger requestedLimit = new AtomicInteger();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine((path, limit) -> {
            requestedPath.set(path);
            requestedLimit.set(limit);
            return insights();
        }, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--format", "json"));

        assertEquals(Path.of("."), requestedPath.get());
        assertEquals(20, requestedLimit.get());
        String json = output.toString(StandardCharsets.UTF_8);
        assertTrue(json.endsWith("\n"));
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= 256 * 1024);
        assertEquals("{\"schemaVersion\":1,\"attemptCount\":3,\"defendedChangeCount\":2,"
                + "\"categories\":[{\"id\":\"decision\",\"averageScore\":92},"
                + "{\"id\":\"counterfactual\",\"averageScore\":54},"
                + "{\"id\":\"test-prediction\",\"averageScore\":31}],"
                + "\"strongestCategory\":\"decision\",\"practiceCategory\":\"test-prediction\","
                + "\"recentOverallScores\":[33,61,84]}\n", json);
    }

    @Test
    void acceptsEveryDocumentedLimitBoundary() {
        AtomicInteger requestedLimit = new AtomicInteger();
        CommandLine commandLine = commandLine((path, limit) -> {
            requestedLimit.set(limit);
            return insights();
        }, new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute(".", "--format", "json", "--limit", "1"));
        assertEquals(1, requestedLimit.get());
        assertEquals(ExitCodes.SUCCESS, commandLine.execute(".", "--format", "json", "--limit", "20"));
        assertEquals(20, requestedLimit.get());
    }

    @Test
    void rejectsOutOfRangeLimitsBeforeTouchingHistory() {
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine((path, limit) -> {
            calls.incrementAndGet();
            return insights();
        }, new ByteArrayOutputStream(), error);

        assertEquals(ExitCodes.INVALID_USAGE,
                commandLine.execute("--format", "json", "--limit", "0"));
        assertEquals(ExitCodes.INVALID_USAGE,
                commandLine.execute("--format", "json", "--limit", "21"));
        assertEquals(0, calls.get());
        assertEquals(("--limit must be between 1 and 20." + System.lineSeparator()).repeat(2),
                error.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsEveryFormatExceptExactJsonWithoutEchoingInput() {
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine((path, limit) -> {
            calls.incrementAndGet();
            return insights();
        }, new ByteArrayOutputStream(), error);

        assertEquals(ExitCodes.INVALID_USAGE,
                commandLine.execute("--format", "private-yaml-marker"));

        assertEquals(0, calls.get());
        String text = error.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Only --format json is supported."));
        assertFalse(text.contains("private-yaml-marker"));
    }

    @Test
    void helpAndVersionTouchNoGitOrPassportHistory() {
        AtomicInteger calls = new AtomicInteger();
        CommandLine commandLine = commandLine((path, limit) -> {
            calls.incrementAndGet();
            throw new AssertionError("help must stay lazy");
        }, new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--help"));
        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--version"));
        assertEquals(0, calls.get());
    }

    @Test
    void mapsTypedLocalFailureWithoutLeakingRepositoryPath() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine((path, limit) -> {
            throw RepositoryLearningInsightsException.localFailure();
        }, new ByteArrayOutputStream(), error);

        assertEquals(ExitCodes.GIT_EXECUTION_FAILED,
                commandLine.execute("C:\\private-repository-marker", "--format", "json"));

        String message = error.toString(StandardCharsets.UTF_8);
        assertEquals("Unable to build repository learning insights." + System.lineSeparator(), message);
        assertFalse(message.contains("private-repository-marker"));
        assertFalse(message.contains("picocli"));
        assertFalse(message.contains("\tat "));
    }

    private static CommandLine commandLine(PassportInsightsCommand.InsightsBuilder builder,
            ByteArrayOutputStream output, ByteArrayOutputStream error) {
        CommandLine commandLine = new CommandLine(
                new PassportInsightsCommand(builder, new RepositoryLearningInsightsJsonCodec()));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));
        commandLine.setParameterExceptionHandler((exception, arguments) -> ExitCodes.INVALID_USAGE);
        return commandLine;
    }

    private static RepositoryLearningInsights insights() {
        return new RepositoryLearningInsights(1, 3, 2, List.of(
                new CategoryLearningInsight("decision", 92),
                new CategoryLearningInsight("counterfactual", 54),
                new CategoryLearningInsight("test-prediction", 31)),
                "decision", "test-prediction", List.of(33, 61, 84));
    }
}
