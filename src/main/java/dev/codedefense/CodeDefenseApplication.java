package dev.codedefense;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.cli.ReportCommand;
import dev.codedefense.cli.RootCommand;
import dev.codedefense.cli.SampleCommand;
import dev.codedefense.cli.StartCommand;
import dev.codedefense.application.DefaultProjectDefenseRunner;
import dev.codedefense.application.ProjectDefenseRunner;
import dev.codedefense.application.RunSampleUseCase;
import dev.codedefense.sample.SampleProjectExtractor;
import java.util.Objects;
import picocli.CommandLine;

public final class CodeDefenseApplication {
    private CodeDefenseApplication() {
    }

    public static void main(String[] args) {
        System.exit(createCommandLine().execute(args));
    }

    public static CommandLine createCommandLine() {
        ProjectDefenseRunner runner = DefaultProjectDefenseRunner.production();
        return createCommandLine(
                new StartCommand(runner),
                new SampleCommand(new RunSampleUseCase(new SampleProjectExtractor(), runner)),
                new ReportCommand());
    }

    /** Creates the CLI object graph with an explicitly supplied start command. */
    public static CommandLine createCommandLine(StartCommand startCommand) {
        return createCommandLine(startCommand, new SampleCommand(), new ReportCommand());
    }

    /** Creates the CLI object graph with explicitly supplied start and report commands. */
    public static CommandLine createCommandLine(StartCommand startCommand, ReportCommand reportCommand) {
        return createCommandLine(startCommand, new SampleCommand(), reportCommand);
    }

    /** Creates the CLI object graph with explicitly supplied command adapters. */
    public static CommandLine createCommandLine(StartCommand startCommand, SampleCommand sampleCommand,
            ReportCommand reportCommand) {
        Objects.requireNonNull(startCommand, "Start command");
        Objects.requireNonNull(sampleCommand, "Sample command");
        Objects.requireNonNull(reportCommand, "Report command");
        CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.addSubcommand("start", startCommand);
        commandLine.addSubcommand("sample", sampleCommand);
        commandLine.addSubcommand("report", reportCommand);
        commandLine.setParameterExceptionHandler((exception, arguments) -> {
            exception.getCommandLine().getErr().println(exception.getMessage());
            exception.getCommandLine().getErr().println("Try 'codedefense --help' for more information.");
            return ExitCodes.INVALID_USAGE;
        });
        return commandLine;
    }
}
