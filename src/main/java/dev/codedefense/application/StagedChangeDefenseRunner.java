package dev.codedefense.application;

import java.io.PrintWriter;
import java.nio.file.Path;

/** Runs the staged-index-only technical defense workflow. */
@FunctionalInterface
public interface StagedChangeDefenseRunner {
    int run(Path repositoryPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err);
}
