package dev.codedefense.cli;

import dev.codedefense.application.DefaultGitChangeDefenseRunner;
import dev.codedefense.application.GitChangeDefenseRunner;
import dev.codedefense.application.StagedChangeDefenseRunner;
import dev.codedefense.application.RetryChangeDefenseRunner;
import dev.codedefense.application.RetryChangeDefenseUseCase;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.RangeSelector;
import dev.codedefense.domain.StagedSelector;
import dev.codedefense.domain.DefenseFocus;
import dev.codedefense.terminal.ConfirmationPrompt;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "prove", mixinStandardHelpOptions = true,
        description = "Prove understanding of an exact Git change.")
public final class ProveCommand implements java.util.concurrent.Callable<Integer> {
    private final Function<Supplier<ConfirmationPrompt>, GitChangeDefenseRunner> runnerFactory;
    private final Supplier<Reader> inputFactory;
    private final Function<Supplier<ConfirmationPrompt>, RetryChangeDefenseRunner> retryFactory;

    static final class SelectorOptions {
        @Option(names = "--staged", description = "Use exactly the staged Git index.") boolean staged;
        @Option(names = "--commit", paramLabel = "REVISION", description = "Use one resolved commit.") String commit;
        @Option(names = "--range", paramLabel = "BASE...HEAD",
                description = "Use a merge-base three-dot range.") String range;
        @Option(names = "--retry", paramLabel = "ATTEMPT_ID",
                description = "Repeat all three categories for an unchanged Passport attempt.") String retry;

        ChangeSelector selector() {
            if (staged) return new StagedSelector();
            if (commit != null) return new CommitSelector(commit);
            return RangeSelector.parse(range);
        }
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    private SelectorOptions selector;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH",
            description = "Git repository path (default: current directory).")
    private Path path;

    @Option(names = "--dry-run", description = "Preview bounded Git context without sending it.")
    private boolean dryRun;

    @Option(names = {"-y", "--yes"}, description = "Skip confirmation; analysis can consume Codex credits.")
    private boolean yes;

    @Option(names = "--focus", defaultValue = "balanced",
            description = "Defense emphasis: balanced, architecture, failure-modes, or testing.")
    private String focus;

    @Spec private CommandSpec commandSpec;

    public ProveCommand() {
        this(confirmation -> DefaultGitChangeDefenseRunner.production(confirmation),
                confirmation -> RetryChangeDefenseUseCase.production(confirmation),
                () -> new InputStreamReader(System.in, StandardCharsets.UTF_8), true);
    }

    public ProveCommand(GitChangeDefenseRunner runner) {
        this(confirmation -> Objects.requireNonNull(runner),
                confirmation -> unavailableRetry(), () -> new InputStreamReader(System.in, StandardCharsets.UTF_8), true);
    }

    public ProveCommand(StagedChangeDefenseRunner runner) {
        this(confirmation -> adapt(runner), confirmation -> unavailableRetry(), () -> new InputStreamReader(System.in, StandardCharsets.UTF_8), true);
    }

    public ProveCommand(Supplier<StagedChangeDefenseRunner> runnerFactory) {
        this(confirmation -> adapt(Objects.requireNonNull(runnerFactory).get()),
                confirmation -> unavailableRetry(), () -> new InputStreamReader(System.in, StandardCharsets.UTF_8), true);
    }

    ProveCommand(Function<Supplier<ConfirmationPrompt>, StagedChangeDefenseRunner> runnerFactory,
            Supplier<Reader> inputFactory) {
        this(confirmation -> adapt(runnerFactory.apply(confirmation)), confirmation -> unavailableRetry(), inputFactory, true);
    }

    private ProveCommand(Function<Supplier<ConfirmationPrompt>, GitChangeDefenseRunner> runnerFactory,
            Function<Supplier<ConfirmationPrompt>, RetryChangeDefenseRunner> retryFactory,
            Supplier<Reader> inputFactory, boolean ignored) {
        this.runnerFactory = Objects.requireNonNull(runnerFactory);
        this.retryFactory = Objects.requireNonNull(retryFactory);
        this.inputFactory = Objects.requireNonNull(inputFactory);
    }

    @Override public Integer call() {
        Supplier<ConfirmationPrompt> confirmation = () -> new PicocliConfirmationPrompt(
                Objects.requireNonNull(inputFactory.get(), "Command input"));
        DefenseFocus selectedFocus;
        try { selectedFocus = DefenseFocus.parse(focus); }
        catch (IllegalArgumentException exception) {
            commandSpec.commandLine().getErr().println("Unknown defense focus.");
            return ExitCodes.INVALID_USAGE;
        }
        if (selector.retry != null) {
            return retryFactory.apply(confirmation).retry(selector.retry, path, dryRun, yes,
                    commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
        }
        return runnerFactory.apply(confirmation).run(path, selector.selector(), selectedFocus, dryRun, yes,
                commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr());
    }

    private static RetryChangeDefenseRunner unavailableRetry() {
        return (id, path, dry, yes, out, err) -> { throw new UnsupportedOperationException("Retry is not configured"); };
    }

    private static GitChangeDefenseRunner adapt(StagedChangeDefenseRunner runner) {
        Objects.requireNonNull(runner, "runner");
        return (path, selected, dryRun, yes, out, err) -> {
            if (!(selected instanceof StagedSelector)) throw new IllegalArgumentException("Staged runner only");
            return runner.run(path, dryRun, yes, out, err);
        };
    }
}
