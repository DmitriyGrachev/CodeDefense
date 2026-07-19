package dev.codedefense.cli;

import dev.codedefense.application.VerifyLatestChangePassportUseCase;
import dev.codedefense.application.ChangePassportVerifier;
import dev.codedefense.application.VerifyLatestGitChangePassportUseCase;
import dev.codedefense.change.GitCliStagedChangeSource;
import dev.codedefense.change.GitCliChangeSource;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Picocli adapter for read-only Change Passport verification. */
@Command(name = "passport", mixinStandardHelpOptions = true,
        description = "Inspect and verify local Change Passports.",
        subcommands = {PassportShowCommand.class, PassportListCommand.class,
                PassportVerifyCommand.class, PassportExportCommand.class, PassportTimelineCommand.class,
                PassportHandoffCommand.class})
public final class PassportCommand implements java.util.concurrent.Callable<Integer> {
    private static final String EXPIRATION_EXPLANATION =
            "A Change Passport is expired when the staged Git index no longer matches its captured identity.";

    private final Supplier<ChangePassportVerifier> verifierFactory;

    @Option(names = "--verify", paramLabel = "PATH",
            description = "Verify the latest Change Passport for this repository.")
    private Path path;

    @Spec
    private CommandSpec commandSpec;

    public PassportCommand() {
        this(() -> { var store = new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                new MarkdownChangePassportRenderer(), Clock.systemUTC());
            return new VerifyLatestGitChangePassportUseCase(new GitCliChangeSource(new JdkProcessExecutor()), store); });
    }

    public PassportCommand(VerifyLatestChangePassportUseCase verifier) {
        this(() -> verifier);
    }

    public PassportCommand(Supplier<? extends ChangePassportVerifier> verifierFactory) {
        Objects.requireNonNull(verifierFactory, "Passport verifier factory");
        this.verifierFactory = () -> verifierFactory.get();
    }

    @Override
    public Integer call() {
        if (path == null) {
            commandSpec.commandLine().getErr().println("Specify a passport subcommand or --verify PATH.");
            return ExitCodes.INVALID_USAGE;
        }
        try {
            var verification = Objects.requireNonNull(verifierFactory.get(), "Passport verifier")
                    .verify(path);
            if (verification.isEmpty()) {
                commandSpec.commandLine().getOut().println("No Change Passport is available for verification yet.");
                return ExitCodes.SUCCESS;
            }
            PassportStatus status = verification.orElseThrow().status();
            commandSpec.commandLine().getOut().println("Change Passport: " + status);
            commandSpec.commandLine().getOut().println(EXPIRATION_EXPLANATION);
            return ExitCodes.SUCCESS;
        } catch (GitChangeException exception) {
            return renderGitFailure(exception);
        } catch (ChangePassportPersistenceException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }

    private int renderGitFailure(GitChangeException exception) {
        return switch (exception.kind()) {
            case INVALID_REPOSITORY -> {
                commandSpec.commandLine().getErr().println("The supplied path is not a readable Git repository.");
                yield ExitCodes.INVALID_PROJECT_PATH;
            }
            case NO_STAGED_CHANGE -> {
                commandSpec.commandLine().getErr().println("No eligible staged source files were found.");
                yield ExitCodes.NO_SUPPORTED_SOURCE_FILES;
            }
            case NO_HEAD, UNSUPPORTED_CHANGE, CHANGED_DURING_CAPTURE, EXECUTION_FAILED, MALFORMED_DATA -> {
                commandSpec.commandLine().getErr().println("Git could not safely capture the staged change.");
                yield ExitCodes.GIT_EXECUTION_FAILED;
            }
        };
    }
}
