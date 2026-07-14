package dev.codedefense.cli;

import dev.codedefense.CodeDefenseApplication;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void placeholderCommandsDoNotThrow() {
        assertDoesNotThrow(() -> CodeDefenseApplication.createCommandLine().execute("start"));
        assertDoesNotThrow(() -> CodeDefenseApplication.createCommandLine().execute("sample"));
        assertDoesNotThrow(() -> CodeDefenseApplication.createCommandLine().execute("report"));
    }

    private CommandLine commandLineWithOutput(ByteArrayOutputStream output) {
        CommandLine commandLine = CodeDefenseApplication.createCommandLine();
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        return commandLine;
    }
}
