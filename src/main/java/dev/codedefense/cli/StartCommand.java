package dev.codedefense.cli;

import dev.codedefense.application.DefaultProjectDefenseRunner;
import dev.codedefense.application.ProjectDefenseRunner;
import java.nio.file.Path;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "start",
        mixinStandardHelpOptions = true,
        description = "Start a technical defense for a local repository.")
public final class StartCommand implements java.util.concurrent.Callable<Integer> {
    private final ProjectDefenseRunner runner;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH", description = "Repository path (default: current directory).")
    private Path path;

    @Option(names = "--dry-run", description = "Discover supported files without starting a defense.")
    private boolean dryRun;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation; analysis can consume Codex credits.")
    private boolean yes;

    @Spec
    private CommandSpec commandSpec;

    public StartCommand() {
        this(DefaultProjectDefenseRunner.production());
    }

    public StartCommand(ProjectDefenseRunner runner) {
        this.runner = Objects.requireNonNull(runner, "Project defense runner");
    }

    public Path path() {
        return path;
    }

    @Override
    public Integer call() {
        return runner.run(path, dryRun, yes,
                commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
    }
}
