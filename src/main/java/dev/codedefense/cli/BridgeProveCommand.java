package dev.codedefense.cli;

import dev.codedefense.application.DefaultGitChangeDefenseRunner;
import dev.codedefense.bridge.BridgeEvent;
import dev.codedefense.bridge.BridgeProtocol;
import dev.codedefense.bridge.BridgeProtocolException;
import dev.codedefense.bridge.BridgeSession;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.DefenseFocus;
import dev.codedefense.domain.RangeSelector;
import dev.codedefense.domain.StagedSelector;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "prove", mixinStandardHelpOptions = true,
        description = "Run a change defense through NDJSON bridge protocol 1.")
public final class BridgeProveCommand implements java.util.concurrent.Callable<Integer> {
    @FunctionalInterface
    public interface BridgeDefenseRunner {
        int run(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
                BridgeSession session);
    }

    private final BridgeDefenseRunner runner;
    private final Supplier<InputStream> inputFactory;
    private final Supplier<OutputStream> outputFactory;
    private final Supplier<OutputStream> errorFactory;

    @Option(names = "--protocol", required = true, paramLabel = "VERSION")
    private int protocolVersion;
    @Option(names = "--staged") private boolean staged;
    @Option(names = "--commit", paramLabel = "REVISION") private String commit;
    @Option(names = "--range", paramLabel = "BASE...HEAD") private String range;
    @Option(names = "--focus", defaultValue = "balanced") private String focus;
    @Option(names = "--dry-run") private boolean dryRun;
    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH")
    private Path path;

    public BridgeProveCommand() {
        this((repository, selector, selectedFocus, previewOnly, session) ->
                        DefaultGitChangeDefenseRunner.productionBridge()
                                .runBridge(repository, selector, selectedFocus, previewOnly, session),
                () -> System.in, () -> System.out, () -> System.err);
    }

    BridgeProveCommand(BridgeDefenseRunner runner, Supplier<InputStream> inputFactory,
            Supplier<OutputStream> outputFactory, Supplier<OutputStream> errorFactory) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.inputFactory = Objects.requireNonNull(inputFactory, "inputFactory");
        this.outputFactory = Objects.requireNonNull(outputFactory, "outputFactory");
        this.errorFactory = Objects.requireNonNull(errorFactory, "errorFactory");
    }

    @Override
    public Integer call() {
        BridgeSession session = new BridgeSession(
                Objects.requireNonNull(inputFactory.get(), "bridge input"),
                Objects.requireNonNull(outputFactory.get(), "bridge output"));
        try {
            if (protocolVersion != BridgeProtocol.VERSION) {
                return invalid(session, "Unsupported protocol version.");
            }
            ChangeSelector selector = selector();
            DefenseFocus selectedFocus = DefenseFocus.parse(focus);
            return runner.run(path, selector, selectedFocus, dryRun, session);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return invalid(session, "Bridge prove options are invalid.");
        } catch (BridgeProtocolException exception) {
            safeDiagnostic("Bridge protocol failed.");
            return invalid(session, exception.getMessage());
        }
    }

    private ChangeSelector selector() {
        int count = (staged ? 1 : 0) + (commit == null ? 0 : 1) + (range == null ? 0 : 1);
        if (count != 1) {
            throw new IllegalArgumentException("Exactly one selector is required");
        }
        if (staged) {
            return new StagedSelector();
        }
        if (commit != null) {
            return new CommitSelector(commit);
        }
        return RangeSelector.parse(range);
    }

    private int invalid(BridgeSession session, String message) {
        session.emit(new BridgeEvent.ErrorEvent(BridgeProtocol.VERSION, "INVALID_REQUEST",
                safeMessage(message), ExitCodes.INVALID_USAGE));
        return ExitCodes.INVALID_USAGE;
    }

    private void safeDiagnostic(String message) {
        PrintWriter error = new PrintWriter(new OutputStreamWriter(
                Objects.requireNonNull(errorFactory.get(), "bridge error"), StandardCharsets.UTF_8), true);
        error.println(message);
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank() || message.length() > 256) {
            return "Bridge request is invalid.";
        }
        return message;
    }
}
