package dev.codedefense.cli;

import dev.codedefense.application.ProjectDefenseRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StartCommandTest {
    @Test
    void commandDelegatesOnceWithParsedFlagsPathAndConfiguredWriters() {
        CapturingRunner runner = new CapturingRunner(41);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new StartCommand(runner));
        PrintWriter out = new PrintWriter(output, true, StandardCharsets.UTF_8);
        PrintWriter err = new PrintWriter(error, true, StandardCharsets.UTF_8);
        commandLine.setOut(out);
        commandLine.setErr(err);

        assertEquals(41, commandLine.execute("--dry-run", "--yes", "fixture path"));

        assertEquals(1, runner.calls);
        assertEquals(Path.of("fixture path"), runner.path);
        assertEquals(true, runner.dryRun);
        assertEquals(true, runner.skipConfirmation);
        assertSame(out, runner.out);
        assertSame(err, runner.err);
    }

    @Test
    void helpDoesNotDelegateToTheWorkflow() {
        CapturingRunner runner = new CapturingRunner(ExitCodes.SUCCESS);

        assertEquals(ExitCodes.SUCCESS, new CommandLine(new StartCommand(runner)).execute("--help"));

        assertEquals(0, runner.calls);
    }

    private static final class CapturingRunner implements ProjectDefenseRunner {
        private final int result;
        private int calls;
        private Path path;
        private boolean dryRun;
        private boolean skipConfirmation;
        private PrintWriter out;
        private PrintWriter err;

        private CapturingRunner(int result) {
            this.result = result;
        }

        @Override
        public int run(Path projectPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err) {
            calls++;
            path = projectPath;
            this.dryRun = dryRun;
            this.skipConfirmation = skipConfirmation;
            this.out = out;
            this.err = err;
            return result;
        }
    }
}
