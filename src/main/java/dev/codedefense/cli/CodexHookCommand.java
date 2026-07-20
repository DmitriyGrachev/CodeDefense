package dev.codedefense.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "codex-hook", hidden = true, mixinStandardHelpOptions = true,
        version = "CodeDefense 0.1.0", subcommands = CodexHookStatusCommand.class)
public final class CodexHookCommand implements Runnable {
    @Spec
    private CommandSpec commandSpec;

    @Override
    public void run() {
        commandSpec.commandLine().usage(commandSpec.commandLine().getOut());
    }
}
