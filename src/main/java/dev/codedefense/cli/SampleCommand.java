package dev.codedefense.cli;

import picocli.CommandLine.Command;

@Command(name = "sample", description = "Run the built-in sample project defense.")
public final class SampleCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Sample is a placeholder; the embedded sample arrives in Iteration 8.");
    }
}
