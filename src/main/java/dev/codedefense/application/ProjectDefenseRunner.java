package dev.codedefense.application;

import java.io.PrintWriter;
import java.nio.file.Path;

/** Runs the command-independent technical-defense workflow for one local project. */
@FunctionalInterface
public interface ProjectDefenseRunner {
    int run(Path projectPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err);
}
