package dev.codedefense.cli;

import dev.codedefense.application.ShowPassportTimelineUseCase;
import dev.codedefense.passport.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "timeline", mixinStandardHelpOptions = true,
        description = "Show attempts for the latest exact Git change.")
public final class PassportTimelineCommand implements Callable<Integer> {
    private final ShowPassportTimelineUseCase useCase;
    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH") private Path path;
    @Spec private CommandSpec spec;
    public PassportTimelineCommand() {
        this(new ShowPassportTimelineUseCase(new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                new MarkdownChangePassportRenderer(), Clock.systemUTC()), new PassportTerminalRenderer()));
    }
    PassportTimelineCommand(ShowPassportTimelineUseCase useCase) { this.useCase = Objects.requireNonNull(useCase); }
    @Override public Integer call() {
        try { return useCase.show(path, spec.commandLine().getOut(), spec.commandLine().getErr()); }
        catch (ChangePassportPersistenceException exception) {
            spec.commandLine().getErr().println(exception.getMessage()); return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }
}
