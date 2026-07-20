package dev.codedefense.cli;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.application.BuildRepositoryLearningInsightsUseCase;
import dev.codedefense.application.RepositoryLearningInsightsException;
import dev.codedefense.change.GitCliStagedChangeSource;
import dev.codedefense.domain.RepositoryLearningInsights;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import dev.codedefense.passport.RepositoryLearningInsightsJsonCodec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Machine-readable repository-local learning history. */
@Command(name = "insights", mixinStandardHelpOptions = true, version = "CodeDefense 0.1.0",
        description = "Show source-free learning insights for this repository.")
public final class PassportInsightsCommand implements Callable<Integer> {
    private final InsightsBuilder builder;
    private final RepositoryLearningInsightsJsonCodec codec;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH",
            description = "Repository path (default: current directory).")
    private Path path;

    @Option(names = "--format", required = true, paramLabel = "FORMAT",
            description = "Output format: json.")
    private String format;

    @Option(names = "--limit", defaultValue = "20", paramLabel = "COUNT",
            description = "Newest complete attempts to aggregate (1-20; default: 20).")
    private int limit;

    @Spec
    private CommandSpec commandSpec;

    public PassportInsightsCommand() {
        this((repository, requestedLimit) -> production().build(repository, requestedLimit),
                new RepositoryLearningInsightsJsonCodec());
    }

    PassportInsightsCommand(InsightsBuilder builder, RepositoryLearningInsightsJsonCodec codec) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public Integer call() {
        if (!"json".equals(format)) {
            commandSpec.commandLine().getErr().println("Only --format json is supported.");
            return ExitCodes.INVALID_USAGE;
        }
        if (limit < 1 || limit > 20) {
            commandSpec.commandLine().getErr().println("--limit must be between 1 and 20.");
            return ExitCodes.INVALID_USAGE;
        }
        try {
            byte[] output = codec.encode(builder.build(path, limit));
            commandSpec.commandLine().getOut().print(new String(output, StandardCharsets.UTF_8));
            commandSpec.commandLine().getOut().flush();
            return ExitCodes.SUCCESS;
        } catch (RepositoryLearningInsightsException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.GIT_EXECUTION_FAILED;
        }
    }

    private static BuildRepositoryLearningInsightsUseCase production() {
        return new BuildRepositoryLearningInsightsUseCase(
                new GitCliStagedChangeSource(new JdkProcessExecutor()),
                new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                        new MarkdownChangePassportRenderer(), Clock.systemUTC()));
    }

    @FunctionalInterface
    interface InsightsBuilder {
        RepositoryLearningInsights build(Path repository, int limit);
    }
}
