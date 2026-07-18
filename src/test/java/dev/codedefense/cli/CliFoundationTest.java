package dev.codedefense.cli;

import dev.codedefense.CodeDefenseApplication;
import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.application.DefaultProjectDefenseRunner;
import dev.codedefense.application.ProjectDefenseRunner;
import dev.codedefense.application.SampleProjectRunner;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.scanner.InvalidProjectPathException;
import dev.codedefense.scanner.NoSupportedSourceFilesException;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.terminal.ProjectAnalysisRenderer;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliFoundationTest {
    @Test
    void rootHelpListsEveryCommand() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLineWithOutput(output);

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--help"));

        String help = output.toString(StandardCharsets.UTF_8);
        assertTrue(help.contains("start"));
        assertTrue(help.contains("sample"));
        assertTrue(help.contains("report"));
    }

    @Test
    void rootRegistersStartSampleAndReportCommands() {
        CommandLine commandLine = CodeDefenseApplication.createCommandLine();

        assertTrue(commandLine.getSubcommands().containsKey("start"));
        assertTrue(commandLine.getSubcommands().containsKey("sample"));
        assertTrue(commandLine.getSubcommands().containsKey("report"));
    }

    @Test
    void invalidOptionReturnsInvalidUsageExitCode() {
        assertEquals(ExitCodes.INVALID_USAGE, CodeDefenseApplication.createCommandLine().execute("--unknown"));
    }

    @Test
    void startDefaultsPathToCurrentDirectory() {
        StartCommand start = new StartCommand();
        CommandLine commandLine = new CommandLine(start);

        assertEquals(ExitCodes.SUCCESS, commandLine.execute());
        assertEquals(Path.of("."), start.path());
    }

    @Test
    void commandsDoNotThrowWithoutSendingSampleContent() {
        assertDoesNotThrow(() -> CodeDefenseApplication.createCommandLine().execute("start"));
        assertDoesNotThrow(() -> CodeDefenseApplication.createCommandLine().execute("sample", "--dry-run"));
        assertDoesNotThrow(() -> CodeDefenseApplication.createCommandLine().execute("report"));
    }

    @Test
    void injectableSampleCommandIsRegisteredAndRootHelpAndVersionDoNotInvokeIt() {
        AtomicInteger sampleCalls = new AtomicInteger();
        ProjectDefenseRunner startRunner = (path, dryRun, skipConfirmation, out, err) -> ExitCodes.SUCCESS;
        SampleProjectRunner sampleRunner = (dryRun, skipConfirmation, out, err) -> {
            sampleCalls.incrementAndGet();
            return ExitCodes.SUCCESS;
        };
        SampleCommand sample = new SampleCommand(sampleRunner);
        CommandLine commandLine = CodeDefenseApplication.createCommandLine(
                new StartCommand(startRunner), sample, new ReportCommand());

        assertSame(sample, commandLine.getSubcommands().get("sample").getCommand());
        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--help"));
        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--version"));
        assertEquals(0, sampleCalls.get());
    }

    @Test
    void dryRunUsesInjectedScannerAndConfiguredOutput() {
        ProjectScanner scanner = (root, policy) -> new ScanSummary(
                root, 1, 0, List.of(new SourceFile(Path.of("pom.xml")))
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(startCommand(scanner));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--dry-run", "."));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Accepted candidates: 1"));
    }

    @Test
    void dryRunMapsInvalidDirectoryToDocumentedExitCode() {
        ProjectScanner scanner = (root, policy) -> {
            throw new InvalidProjectPathException("Project path does not exist: " + root);
        };

        assertEquals(ExitCodes.INVALID_PROJECT_PATH,
                new CommandLine(startCommand(scanner)).execute("--dry-run", "missing"));
    }

    @Test
    void dryRunMapsEmptyProjectToDocumentedExitCode() {
        ProjectScanner scanner = (root, policy) -> {
            throw new NoSupportedSourceFilesException("No supported source files found: " + root);
        };

        assertEquals(ExitCodes.NO_SUPPORTED_SOURCE_FILES,
                new CommandLine(startCommand(scanner)).execute("--dry-run", "."));
    }

    private CommandLine commandLineWithOutput(ByteArrayOutputStream output) {
        CommandLine commandLine = CodeDefenseApplication.createCommandLine();
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        return commandLine;
    }

    private static StartCommand startCommand(ProjectScanner scanner) {
        return new StartCommand(new DefaultProjectDefenseRunner(
                scanner,
                new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()),
                prompt -> false,
                new ProjectAnalysisRenderer(),
                output -> {
                    throw new AssertionError("Dry-run tests must not create a runtime");
                }));
    }
}
