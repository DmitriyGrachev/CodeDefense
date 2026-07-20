package dev.codedefense.cli;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.GitCliChangeSource;
import dev.codedefense.ci.CiPassportException;
import dev.codedefense.ci.CiPassportPolicy;
import dev.codedefense.ci.CommitPassportContinuityChecker;
import dev.codedefense.ci.GitCommitRangeReader;
import dev.codedefense.ci.PassportContinuityCheck;
import dev.codedefense.ci.PassportContinuityRenderer;
import dev.codedefense.ci.PassportTrailerParser;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "ci-check", mixinStandardHelpOptions = true,
        description = "Check source-free Passport fingerprint continuity across a commit range.")
public final class PassportCiCheckCommand implements Callable<Integer> {
    @Option(names = "--base", required = true, paramLabel = "REV") String base;
    @Option(names = "--head", required = true, paramLabel = "REV") String head;
    @Option(names = "--policy", defaultValue = "advisory", paramLabel = "advisory|required") String policy;
    @Option(names = "--format", defaultValue = "text", paramLabel = "text|json|github") String format;
    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH") Path repository;
    @Spec CommandSpec spec;

    private final PassportContinuityCheck checker;
    private final PassportContinuityRenderer renderer;

    public PassportCiCheckCommand() {
        JdkProcessExecutor executor = new JdkProcessExecutor();
        this.checker = new CommitPassportContinuityChecker(new GitCommitRangeReader(executor),
                new GitCliChangeSource(executor), new PassportTrailerParser());
        this.renderer = new PassportContinuityRenderer();
    }

    PassportCiCheckCommand(PassportContinuityCheck checker, PassportContinuityRenderer renderer) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override public Integer call() {
        try {
            CiPassportPolicy selectedPolicy = CiPassportPolicy.parse(policy);
            PassportContinuityRenderer.Format selectedFormat = PassportContinuityRenderer.Format.parse(format);
            var result = checker.check(repository, base, head);
            renderer.render(result, selectedPolicy, selectedFormat, spec.commandLine().getOut());
            return selectedPolicy.exitCode(result);
        } catch (IllegalArgumentException exception) {
            spec.commandLine().getErr().println("Invalid Passport CI check option.");
            return ExitCodes.INVALID_USAGE;
        } catch (CiPassportException | GitChangeException exception) {
            spec.commandLine().getErr().println("Git commit history is unavailable for Passport continuity checking.");
            return ExitCodes.GIT_EXECUTION_FAILED;
        }
    }
}
