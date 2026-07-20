package dev.codedefense.application;

import dev.codedefense.terminal.InterviewOutput;
import dev.codedefense.terminal.UserInput;
import java.io.PrintWriter;

/** Creates the interactive runtime only after a project has been approved for analysis. */
@FunctionalInterface
public interface CodeDefenseRuntimeProvider {
    CodeDefenseRuntime create(PrintWriter output);

    default CodeDefenseRuntime create(UserInput input, InterviewOutput output) {
        throw new UnsupportedOperationException("Structured interview adapters are not configured.");
    }
}
