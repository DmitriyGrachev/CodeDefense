package dev.codedefense.application;

import java.io.PrintWriter;

/** Command-facing application boundary for the embedded sample workflow. */
@FunctionalInterface
public interface SampleProjectRunner {
    int run(boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err);
}
