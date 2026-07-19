package dev.codedefense.cli;

import dev.codedefense.application.ListChangePassportsUseCase;
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
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "list", mixinStandardHelpOptions = true,
        description = "List recent local Change Passports.")
public final class PassportListCommand implements Callable<Integer> {
    private final ListChangePassportsUseCase useCase;
    @Parameters(index = "0", defaultValue = ".", paramLabel = "PATH") private Path path;
    @Option(names = "--limit", defaultValue = "10", description = "Number of receipts (1-50).")
    private int limit;
    @Spec private CommandSpec commandSpec;
    public PassportListCommand() { this(new ListChangePassportsUseCase(
            new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                    new MarkdownChangePassportRenderer(), Clock.systemUTC()),
            new PassportTerminalRenderer())); }
    PassportListCommand(ListChangePassportsUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }
    @Override public Integer call() {
        try { return useCase.list(path, limit, commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr()); }
        catch (IllegalArgumentException exception) {
            commandSpec.commandLine().getErr().println("--limit must be between 1 and 50.");
            return ExitCodes.INVALID_USAGE;
        } catch (ChangePassportPersistenceException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }
}
