package dev.codedefense.cli;

import picocli.CommandLine.Command;

@Command(name = "report", description = "Show the latest CodeDefense report.")
public final class ReportCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Report is a placeholder; report storage arrives in Iteration 7.");
    }
}
