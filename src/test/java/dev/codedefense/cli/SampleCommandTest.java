package dev.codedefense.cli;

import dev.codedefense.application.SampleProjectRunner;
import dev.codedefense.sample.SampleProjectException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleCommandTest {
    @Test
    void helpDoesNotRunTheSampleWorkflow() {
        CountingSampleRunner runner = new CountingSampleRunner(ExitCodes.SUCCESS);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(runner, output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--help"));

        assertEquals(0, runner.calls);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("--dry-run"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("--yes"));
    }

    @Test
    void dryRunDelegatesFlagsAndConfiguredWriters() {
        CountingSampleRunner runner = new CountingSampleRunner(41);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(runner, output, new ByteArrayOutputStream());

        assertEquals(41, commandLine.execute("--dry-run"));

        assertEquals(1, runner.calls);
        assertTrue(runner.dryRun);
        assertTrue(!runner.skipConfirmation);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("sample workflow output"));
    }

    @Test
    void defaultInvocationForwardsFalseFlagsAndExactWorkflowExitCodes() {
        for (int exitCode : new int[] {ExitCodes.SUCCESS, ExitCodes.CODEX_NOT_INSTALLED,
                ExitCodes.CODEX_NOT_AUTHENTICATED, ExitCodes.CODEX_EXECUTION_FAILED,
                ExitCodes.INVALID_MODEL_RESPONSE, ExitCodes.REPORT_PERSISTENCE_FAILED, ExitCodes.CANCELLED}) {
            CountingSampleRunner runner = new CountingSampleRunner(exitCode);

            assertEquals(exitCode, commandLine(runner, new ByteArrayOutputStream(), new ByteArrayOutputStream()).execute());
            assertEquals(1, runner.calls);
            assertTrue(!runner.dryRun);
            assertTrue(!runner.skipConfirmation);
        }
    }

    @Test
    void yesBypassesConfirmationForTheDelegatedWorkflow() {
        CountingSampleRunner runner = new CountingSampleRunner(ExitCodes.SUCCESS);

        assertEquals(ExitCodes.SUCCESS, commandLine(runner, new ByteArrayOutputStream(), new ByteArrayOutputStream())
                .execute("--yes"));

        assertEquals(1, runner.calls);
        assertTrue(runner.skipConfirmation);
    }

    @Test
    void shortYesAliasBypassesConfirmationForTheDelegatedWorkflow() {
        CountingSampleRunner runner = new CountingSampleRunner(ExitCodes.SUCCESS);

        assertEquals(ExitCodes.SUCCESS, commandLine(runner, new ByteArrayOutputStream(), new ByteArrayOutputStream())
                .execute("-y"));

        assertEquals(1, runner.calls);
        assertTrue(runner.skipConfirmation);
    }

    @Test
    void samplePreparationFailureUsesTheSafeExecutionExitCodeAndErrorWriter() {
        SampleProjectRunner runner = (dryRun, skipConfirmation, out, err) -> {
            throw SampleProjectException.unavailable();
        };
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED,
                commandLine(runner, new ByteArrayOutputStream(), error).execute("--dry-run"));

        assertTrue(error.toString(StandardCharsets.UTF_8)
                .contains("Embedded sample project is unavailable."));
    }

    @Test
    void sampleCleanupFailureUsesOnlyTheFixedSafeErrorMessage() {
        SampleProjectRunner runner = (dryRun, skipConfirmation, out, err) -> {
            throw SampleProjectException.cleanupFailure(new IOException("private workspace path"));
        };
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        assertEquals(ExitCodes.CODEX_EXECUTION_FAILED,
                commandLine(runner, new ByteArrayOutputStream(), error).execute("--dry-run"));

        String text = error.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Temporary sample project could not be removed."));
        assertTrue(!text.contains("private workspace path"));
        assertTrue(!text.contains("\tat "));
    }

    @Test
    void unexpectedPathArgumentIsInvalidUsageAndDoesNotInvokeTheWorkflow() {
        CountingSampleRunner runner = new CountingSampleRunner(ExitCodes.SUCCESS);

        assertEquals(ExitCodes.INVALID_USAGE,
                commandLine(runner, new ByteArrayOutputStream(), new ByteArrayOutputStream()).execute("unexpected-path"));

        assertEquals(0, runner.calls);
    }

    private static CommandLine commandLine(SampleProjectRunner runner,
            ByteArrayOutputStream output, ByteArrayOutputStream error) {
        CommandLine commandLine = new CommandLine(new SampleCommand(runner));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));
        return commandLine;
    }

    private static final class CountingSampleRunner implements SampleProjectRunner {
        private final int result;
        private int calls;
        private boolean dryRun;
        private boolean skipConfirmation;

        private CountingSampleRunner(int result) {
            this.result = result;
        }

        @Override
        public int run(boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err) {
            calls++;
            this.dryRun = dryRun;
            this.skipConfirmation = skipConfirmation;
            out.println("sample workflow output");
            return result;
        }
    }
}
