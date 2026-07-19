package dev.codedefense.cli;

import dev.codedefense.application.DefaultProjectDefenseRunner;
import dev.codedefense.application.RunSampleUseCase;
import dev.codedefense.application.SampleProjectRunner;
import dev.codedefense.sample.SampleProjectException;
import dev.codedefense.sample.SampleProjectExtractor;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "sample",
        mixinStandardHelpOptions = true,
        description = "Run a technical defense against the built-in sample project.")
public final class SampleCommand implements java.util.concurrent.Callable<Integer> {
    private final SampleProjectRunner sampleProjectRunner;

    @Option(names = "--dry-run", description = "Preview the built-in sample without invoking Codex.")
    private boolean dryRun;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation; the sample analysis can consume Codex credits.")
    private boolean yes;

    @Spec
    private CommandSpec commandSpec;

    public SampleCommand() {
        this(new RunSampleUseCase(new SampleProjectExtractor(), DefaultProjectDefenseRunner.production()));
    }

    public SampleCommand(SampleProjectRunner sampleProjectRunner) {
        this.sampleProjectRunner = Objects.requireNonNull(sampleProjectRunner, "Sample project runner");
    }

    @Override
    public Integer call() {
        try {
            return sampleProjectRunner.run(dryRun, yes,
                    commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
        } catch (SampleProjectException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.CODEX_EXECUTION_FAILED;
        }
    }
}
