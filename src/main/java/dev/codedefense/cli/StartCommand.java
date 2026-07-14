package dev.codedefense.cli;

import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "start", description = "Start a technical defense for a local repository.")
public final class StartCommand implements Runnable {
    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH", description = "Repository path (default: current directory).")
    private Path path;

    public Path path() {
        return path;
    }

    @Override
    public void run() {
        System.out.println("Start is a placeholder; repository scanning begins in Iteration 2.");
    }
}
