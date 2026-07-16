package dev.codedefense.terminal;

import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.regex.Pattern;

/** Renders only the safe, high-level overview prepared for the technical defense. */
public final class ProjectAnalysisRenderer {
    private static final Pattern OSC = Pattern.compile("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)");
    private static final Pattern CSI = Pattern.compile("(?:\\u001B\\[|\\u009B)[0-?]*[ -/]*[@-~]");

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
        output.println("The adaptive defense will be connected in Iteration 6.");
    }

    private static String safe(String value) {
        String withoutSequences = CSI.matcher(OSC.matcher(value).replaceAll("")).replaceAll("");
        String normalizedLines = withoutSequences.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder safe = new StringBuilder(normalizedLines.length());
        normalizedLines.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t' || codePoint == 0x2028 || codePoint == 0x2029) {
                safe.append(' ');
            } else if (!Character.isISOControl(codePoint) && !isBidiFormatControl(codePoint)) {
                safe.appendCodePoint(codePoint);
            }
        });
        return safe.toString().strip();
    }

    private static boolean isBidiFormatControl(int codePoint) {
        return codePoint == 0x061C
                || codePoint >= 0x200E && codePoint <= 0x200F
                || codePoint >= 0x202A && codePoint <= 0x202E
                || codePoint >= 0x2066 && codePoint <= 0x2069;
    }
}
