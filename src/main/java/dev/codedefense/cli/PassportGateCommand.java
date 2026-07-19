package dev.codedefense.cli;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.application.EvaluateStagedPassportGateUseCase;
import dev.codedefense.change.GitCliStagedChangeSource;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import dev.codedefense.passport.StagedPassportGateJsonCodec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Machine-readable, source-free staged index gate. */
@Command(name = "gate", mixinStandardHelpOptions = true, version = "CodeDefense 0.1.0",
        description = "Evaluate the exact staged Git index against local Change Passports.")
public final class PassportGateCommand implements Callable<Integer> {
    private final EvaluateStagedPassportGateUseCase useCase;
    private final StagedPassportGateJsonCodec codec;

    @Option(names = "--staged", required = true,
            description = "Evaluate only the exact staged Git index.")
    private boolean staged;

    @Option(names = "--format", required = true, paramLabel = "FORMAT",
            description = "Output format: json.")
    private String format;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH",
            description = "Repository path (default: current directory).")
    private Path path;

    @Spec
    private CommandSpec commandSpec;

    public PassportGateCommand() {
        this(production(), new StagedPassportGateJsonCodec());
    }

    PassportGateCommand(EvaluateStagedPassportGateUseCase useCase, StagedPassportGateJsonCodec codec) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public Integer call() {
        if (!"json".equals(format)) {
            commandSpec.commandLine().getErr().println("Only --format json is supported.");
            return ExitCodes.INVALID_USAGE;
        }
        byte[] output = codec.encode(useCase.evaluate(path));
        commandSpec.commandLine().getOut().print(new String(output, StandardCharsets.UTF_8));
        commandSpec.commandLine().getOut().flush();
        return ExitCodes.SUCCESS;
    }

    private static EvaluateStagedPassportGateUseCase production() {
        return new EvaluateStagedPassportGateUseCase(new GitCliStagedChangeSource(new JdkProcessExecutor()),
                new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                        new MarkdownChangePassportRenderer(), Clock.systemUTC()));
    }
}
