package dev.codedefense;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.cli.ReportCommand;
import dev.codedefense.cli.RootCommand;
import dev.codedefense.cli.SampleCommand;
import dev.codedefense.cli.StartCommand;
import java.util.Objects;
import picocli.CommandLine;

public final class CodeDefenseApplication {
    private CodeDefenseApplication() {
    }

    public static void main(String[] args) {
        System.exit(createCommandLine().execute(args));
    }

    public static CommandLine createCommandLine() {
        return createCommandLine(new StartCommand(), new ReportCommand());
    }

    /** Creates the CLI object graph with an explicitly supplied start command. */
    public static CommandLine createCommandLine(StartCommand startCommand) {
        return createCommandLine(startCommand, new ReportCommand());
    }

    /** Creates the CLI object graph with explicitly supplied start and report commands. */
    public static CommandLine createCommandLine(StartCommand startCommand, ReportCommand reportCommand) {
        Objects.requireNonNull(startCommand, "Start command");
        Objects.requireNonNull(reportCommand, "Report command");
        CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.addSubcommand("start", startCommand);
        commandLine.addSubcommand("sample", new SampleCommand());
        commandLine.addSubcommand("report", reportCommand);
        commandLine.setParameterExceptionHandler((exception, arguments) -> {
            exception.getCommandLine().getErr().println(exception.getMessage());
            exception.getCommandLine().getErr().println("Try 'codedefense --help' for more information.");
            return ExitCodes.INVALID_USAGE;
        });
        return commandLine;
    }
}
