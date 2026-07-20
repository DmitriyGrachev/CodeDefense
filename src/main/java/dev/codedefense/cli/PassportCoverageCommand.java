package dev.codedefense.cli;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.application.EvidenceCoverageView;
import dev.codedefense.application.ShowEvidenceCoverageUseCase;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.GitCliChangeSource;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.EvidenceCoverageTerminalRenderer;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "coverage", mixinStandardHelpOptions = true, version = "CodeDefense 0.1.0",
        description = "Show source-free evidence coverage for the latest Change Passport.")
public final class PassportCoverageCommand implements Callable<Integer> {
    @FunctionalInterface interface CoverageLoader { EvidenceCoverageView load(Path repository); }
    private final CoverageLoader loader;
    private final EvidenceCoverageTerminalRenderer renderer = new EvidenceCoverageTerminalRenderer();
    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH") private Path path;
    @Option(names = "--format", defaultValue = "text", paramLabel = "FORMAT") private String format;
    @Spec private CommandSpec commandSpec;

    public PassportCoverageCommand() {
        this(repository -> production().load(repository));
    }

    PassportCoverageCommand(CoverageLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    @Override public Integer call() {
        if (!"text".equals(format) && !"json".equals(format)) {
            commandSpec.commandLine().getErr().println("Coverage format must be text or json.");
            return ExitCodes.INVALID_USAGE;
        }
        try {
            commandSpec.commandLine().getOut().print(renderer.render(loader.load(path), format));
            commandSpec.commandLine().getOut().flush();
            return ExitCodes.SUCCESS;
        } catch (GitChangeException exception) {
            commandSpec.commandLine().getErr().println("Git could not verify the selected Passport change.");
            return ExitCodes.GIT_EXECUTION_FAILED;
        } catch (ChangePassportPersistenceException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }

    private static ShowEvidenceCoverageUseCase production() {
        var source = new GitCliChangeSource(new JdkProcessExecutor());
        var store = new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                new MarkdownChangePassportRenderer(), Clock.systemUTC());
        return new ShowEvidenceCoverageUseCase(source, store);
    }
}
