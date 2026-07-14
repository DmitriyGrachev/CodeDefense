package dev.codedefense;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.cli.RootCommand;
import picocli.CommandLine;

public final class CodeDefenseApplication {
    private CodeDefenseApplication() {
    }

    public static void main(String[] args) {
        System.exit(createCommandLine().execute(args));
    }

    public static CommandLine createCommandLine() {
        CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.setParameterExceptionHandler((exception, arguments) -> {
            exception.getCommandLine().getErr().println(exception.getMessage());
            exception.getCommandLine().getErr().println("Try 'codedefense --help' for more information.");
            return ExitCodes.INVALID_USAGE;
        });
        return commandLine;
    }
}
