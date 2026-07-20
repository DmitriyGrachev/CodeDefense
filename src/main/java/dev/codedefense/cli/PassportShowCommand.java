package dev.codedefense.cli;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.application.ShowLatestChangePassportUseCase;
import dev.codedefense.application.VerifyLatestGitChangePassportUseCase;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.GitCliChangeSource;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import dev.codedefense.passport.PassportTerminalRenderer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

@Command(name = "show", mixinStandardHelpOptions = true,
        description = "Show the latest local Change Passport.")
public final class PassportShowCommand implements Callable<Integer> {
    private final ShowLatestChangePassportUseCase useCase;
    @Parameters(index = "0", defaultValue = ".", paramLabel = "PATH") private Path path;
    @Option(names = "--format", defaultValue = "text", description = "Output format: text or json.")
    private String format;
    @Spec private CommandSpec commandSpec;

    public PassportShowCommand() { this(production()); }
    PassportShowCommand(ShowLatestChangePassportUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }
    @Override public Integer call() {
        try {
            if (format.equalsIgnoreCase("json")) return useCase.showJson(path, commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
            if (!format.equalsIgnoreCase("text")) {
                commandSpec.commandLine().getErr().println("--format must be text or json."); return ExitCodes.INVALID_USAGE;
            }
            return useCase.show(path, commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
        }
        catch (GitChangeException exception) { return PassportVerifyCommand.renderGitFailure(exception, commandSpec); }
        catch (ChangePassportPersistenceException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }
    private static ShowLatestChangePassportUseCase production() {
        var source = new GitCliChangeSource(new JdkProcessExecutor());
        var store = new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                new MarkdownChangePassportRenderer(), Clock.systemUTC());
        return new ShowLatestChangePassportUseCase(store,
                new VerifyLatestGitChangePassportUseCase(source, store), new PassportTerminalRenderer());
    }
}
