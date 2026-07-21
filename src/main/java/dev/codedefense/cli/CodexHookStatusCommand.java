package dev.codedefense.cli;

import dev.codedefense.application.StagedPassportGateEvaluator;
import dev.codedefense.codexhook.CodexHookStatusRenderer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "status", mixinStandardHelpOptions = true, version = "CodeDefense 0.1.1",
        description = "Render source-free staged Passport status for a Codex hook.")
public final class CodexHookStatusCommand implements Callable<Integer> {
    private final Supplier<? extends StagedPassportGateEvaluator> evaluatorFactory;
    private final CodexHookStatusRenderer renderer;

    @Spec
    private CommandSpec commandSpec;

    public CodexHookStatusCommand() {
        this(StagedPassportGateRuntimeFactory::create, new CodexHookStatusRenderer());
    }

    CodexHookStatusCommand(Supplier<? extends StagedPassportGateEvaluator> evaluatorFactory,
            CodexHookStatusRenderer renderer) {
        this.evaluatorFactory = Objects.requireNonNull(evaluatorFactory, "evaluatorFactory");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public Integer call() {
        try {
            StagedPassportGateEvaluator evaluator = Objects.requireNonNull(
                    evaluatorFactory.get(), "evaluator");
            Optional<byte[]> output = renderer.render(evaluator.evaluate(Path.of(".")));
            if (output.isPresent()) {
                commandSpec.commandLine().getOut().print(
                        new String(output.orElseThrow(), StandardCharsets.UTF_8));
                commandSpec.commandLine().getOut().flush();
            }
            return ExitCodes.SUCCESS;
        } catch (RuntimeException exception) {
            commandSpec.commandLine().getErr().println("CodeDefense hook status is unavailable.");
            return ExitCodes.GIT_EXECUTION_FAILED;
        }
    }
}
