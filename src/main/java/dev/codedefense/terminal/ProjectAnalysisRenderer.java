package dev.codedefense.terminal;

import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import java.io.PrintWriter;
import java.util.Objects;

/** Renders only the safe, high-level overview prepared for the technical defense. */
public final class ProjectAnalysisRenderer {
    public void render(ProjectAnalysis analysis, PrintWriter output) {
        Objects.requireNonNull(analysis, "Project analysis");
        Objects.requireNonNull(output, "Command output");

        output.printf("Project: %s%nType: %s%n%n", safe(analysis.projectName()), safe(analysis.projectType()));
        output.println("Summary");
        output.println(safe(analysis.summary()));
        output.println();
        output.println("Main flow");
        for (int index = 0; index < analysis.mainFlow().size(); index++) {
            output.printf("%d. %s%n", index + 1, safe(analysis.mainFlow().get(index)));
        }
        output.println();
        output.println("Key components");
        for (ProjectComponent component : analysis.components()) {
            output.printf("- %s [%s]%n", safe(component.name()), safe(component.kind()));
            output.printf("  %s%n", safe(component.responsibility()));
            output.printf("  Paths: %s%n", component.paths().stream().map(ProjectAnalysisRenderer::safe).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        output.println();
        output.println("Critical topics");
        for (String topic : analysis.criticalTopics()) {
            output.printf("- %s%n", safe(topic));
        }
        output.println();
        output.printf("Prepared technical questions: %d%n%n", analysis.questions().size());
        output.println("Project analysis completed.");
    }

    private static String safe(String value) {
        return TerminalTextSanitizer.singleLine(value);
    }
}
