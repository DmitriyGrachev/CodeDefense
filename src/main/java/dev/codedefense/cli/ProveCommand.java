package dev.codedefense.cli;

import dev.codedefense.application.DefaultStagedChangeDefenseRunner;
import dev.codedefense.application.StagedChangeDefenseRunner;
import java.nio.file.Path;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Function;
import dev.codedefense.terminal.ConfirmationPrompt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Picocli adapter for a staged-index technical defense. */
@Command(name = "prove", mixinStandardHelpOptions = true,
        description = "Prove understanding of a staged Git change.")
public final class ProveCommand implements java.util.concurrent.Callable<Integer> {
    private final Function<Supplier<ConfirmationPrompt>, StagedChangeDefenseRunner> runnerFactory;
    private final Supplier<Reader> inputFactory;

    @Option(names = "--staged", required = true, description = "Use exactly the staged Git index.")
    private boolean staged;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH",
            description = "Git repository path (default: current directory).")
    private Path path;

    @Option(names = "--dry-run", description = "Preview bounded staged context without sending it.")
    private boolean dryRun;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation; analysis can consume Codex credits.")
    private boolean yes;

    @Spec
    private CommandSpec commandSpec;

    public ProveCommand() {
        this(confirmationFactory -> DefaultStagedChangeDefenseRunner.production(confirmationFactory),
                () -> new InputStreamReader(System.in, StandardCharsets.UTF_8), true);
    }

    public ProveCommand(StagedChangeDefenseRunner runner) {
        this(confirmationFactory -> runner, () -> new InputStreamReader(System.in, StandardCharsets.UTF_8), true);
    }

    public ProveCommand(Supplier<StagedChangeDefenseRunner> runnerFactory) {
        this(confirmationFactory -> Objects.requireNonNull(runnerFactory, "Staged defense runner factory").get(),
                () -> new InputStreamReader(System.in, StandardCharsets.UTF_8), true);
    }

    ProveCommand(Function<Supplier<ConfirmationPrompt>, StagedChangeDefenseRunner> runnerFactory,
            Supplier<Reader> inputFactory) {
        this(runnerFactory, inputFactory, true);
    }

    private ProveCommand(Function<Supplier<ConfirmationPrompt>, StagedChangeDefenseRunner> runnerFactory,
            Supplier<Reader> inputFactory, boolean ignored) {
        this.runnerFactory = Objects.requireNonNull(runnerFactory, "Staged defense runner factory");
        this.inputFactory = Objects.requireNonNull(inputFactory, "Command input factory");
    }

    @Override
    public Integer call() {
        Supplier<ConfirmationPrompt> confirmationFactory =
                () -> new PicocliConfirmationPrompt(Objects.requireNonNull(inputFactory.get(), "Command input"));
        return Objects.requireNonNull(runnerFactory.apply(confirmationFactory), "Staged defense runner")
                .run(path, dryRun, yes, commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
    }
}
