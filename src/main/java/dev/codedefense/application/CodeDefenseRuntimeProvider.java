package dev.codedefense.application;

import java.io.PrintWriter;

/** Creates the interactive runtime only after a project has been approved for analysis. */
@FunctionalInterface
public interface CodeDefenseRuntimeProvider {
    CodeDefenseRuntime create(PrintWriter output);
}
