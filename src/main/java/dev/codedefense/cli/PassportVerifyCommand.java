package dev.codedefense.cli;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.application.ChangePassportVerifier;
import dev.codedefense.application.VerifyLatestGitChangePassportUseCase;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.GitCliChangeSource;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "verify", mixinStandardHelpOptions = true,
        description = "Verify the latest Passport against the selected Git change.")
public final class PassportVerifyCommand implements Callable<Integer> {
    private final ChangePassportVerifier useCase;
    @Parameters(index = "0", defaultValue = ".", paramLabel = "PATH") private Path path;
    @Spec private CommandSpec commandSpec;
    public PassportVerifyCommand() { this(production()); }
    PassportVerifyCommand(ChangePassportVerifier useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }
    @Override public Integer call() {
        try {
            var result = useCase.verify(path);
            if (result.isEmpty()) {
                commandSpec.commandLine().getOut().println("No Change Passport is available for verification yet.");
            } else {
                PassportStatus status = result.orElseThrow().status();
                commandSpec.commandLine().getOut().println("Change Passport: " + status);
                commandSpec.commandLine().getOut().println(
                        "A Change Passport is expired when the selected Git change no longer matches its captured identity.");
            }
            return ExitCodes.SUCCESS;
        } catch (GitChangeException exception) { return renderGitFailure(exception, commandSpec); }
        catch (ChangePassportPersistenceException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }
    static int renderGitFailure(GitChangeException exception, CommandSpec spec) {
        return switch (exception.kind()) {
            case INVALID_REPOSITORY -> { spec.commandLine().getErr().println("The supplied path is not a readable Git repository."); yield ExitCodes.INVALID_PROJECT_PATH; }
            case NO_STAGED_CHANGE -> { spec.commandLine().getErr().println("No eligible staged source files were found."); yield ExitCodes.NO_SUPPORTED_SOURCE_FILES; }
            case NO_HEAD, UNSUPPORTED_CHANGE, CHANGED_DURING_CAPTURE, EXECUTION_FAILED, MALFORMED_DATA -> { spec.commandLine().getErr().println("Git could not safely capture the staged change."); yield ExitCodes.GIT_EXECUTION_FAILED; }
        };
    }
    private static ChangePassportVerifier production() {
        var store = new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                new MarkdownChangePassportRenderer(), Clock.systemUTC());
        return new VerifyLatestGitChangePassportUseCase(new GitCliChangeSource(new JdkProcessExecutor()), store);
    }
}
