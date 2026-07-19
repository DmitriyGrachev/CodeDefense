package dev.codedefense.application;

import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.DefenseFocus;
import java.io.PrintWriter;
import java.nio.file.Path;

@FunctionalInterface
public interface GitChangeDefenseRunner {
    int run(Path repositoryPath, ChangeSelector selector, boolean dryRun, boolean skipConfirmation,
            PrintWriter out, PrintWriter err);
    default int run(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        return run(repositoryPath, selector, dryRun, skipConfirmation, out, err);
    }
    default int run(Path repositoryPath, ChangeSelector selector, DefenseFocus focus, boolean dryRun,
            boolean skipConfirmation, CodexProvenanceRequest provenance, PrintWriter out, PrintWriter err) {
        return run(repositoryPath, selector, focus, dryRun, skipConfirmation, out, err);
    }
}
