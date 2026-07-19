package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class PassportHandoffCommandTest {
    @Test
    void helpRegistersThreeLazyCommands() {
        CommandLine command = new CommandLine(new PassportHandoffCommand());

        assertEquals(ExitCodes.SUCCESS, command.execute("--help"));
        assertTrue(command.getSubcommands().keySet().containsAll(java.util.Set.of("create", "inspect", "match")));
    }

    @Test
    void createHelpDocumentsRequiredHandoffExtension() {
        StringWriter output = new StringWriter();
        CommandLine command = new CommandLine(new PassportHandoffCreateCommand());
        command.setOut(new PrintWriter(output, true));

        assertEquals(ExitCodes.SUCCESS, command.execute("--help"));
        assertTrue(output.toString().contains("*.cdhandoff.json"));
    }
}
