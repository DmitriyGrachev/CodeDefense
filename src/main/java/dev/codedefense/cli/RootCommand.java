package dev.codedefense.cli;

import picocli.CommandLine.Command;

@Command(
        name = "codedefense",
        mixinStandardHelpOptions = true,
        version = "CodeDefense 0.1.0",
        description = "Prove that you understand your AI-assisted code."
)
public final class RootCommand implements Runnable {
    @Override
    public void run() {
        // The root command intentionally has no workflow in Iteration 1.
    }
}
