package dev.codedefense.cli;

import picocli.CommandLine.Command;

/** Adapter-facing commands. Hidden from the end-user root synopsis. */
@Command(name = "bridge", hidden = true, mixinStandardHelpOptions = true,
        description = "Versioned local adapter bridge.", subcommands = BridgeProveCommand.class)
public final class BridgeCommand implements Runnable {
    @Override
    public void run() {
        // Picocli renders help when explicitly requested; workflows live in subcommands.
    }
}
