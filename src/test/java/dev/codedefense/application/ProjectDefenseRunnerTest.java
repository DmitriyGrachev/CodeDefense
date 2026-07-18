package dev.codedefense.application;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.scanner.InvalidProjectPathException;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.terminal.ConfirmationPrompt;
import dev.codedefense.terminal.ProjectAnalysisRenderer;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDefenseRunnerTest {
    @TempDir
    Path root;

    @Test
    void dryRunPreviewsSnapshotWithoutConfirmationOrRuntime() throws Exception {
        CountingConfirmation confirmation = new CountingConfirmation(false);
        CountingRuntimeProvider runtimeProvider = new CountingRuntimeProvider();
        ProjectDefenseRunner runner = runner(validScanner(), confirmation, runtimeProvider);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, runner.run(root, true, false, writer(output), writer(new ByteArrayOutputStream())));

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Project: " + root.getFileName()));
        assertTrue(text.contains("Selected files: 1 / 30"));
        assertTrue(text.contains("No source content was sent."));
        assertTrue(text.contains("Codex was not invoked."));
        assertEquals(0, confirmation.calls);
        assertEquals(0, runtimeProvider.calls);
    }

    @Test
    void declinedConfirmationDoesNotCreateRuntime() throws Exception {
        CountingConfirmation confirmation = new CountingConfirmation(false);
        CountingRuntimeProvider runtimeProvider = new CountingRuntimeProvider();
        ProjectDefenseRunner runner = runner(validScanner(), confirmation, runtimeProvider);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, runner.run(root, false, false, writer(output), writer(new ByteArrayOutputStream())));

        assertEquals(1, confirmation.calls);
        assertEquals(0, runtimeProvider.calls);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Cancelled before any source content was sent."));
    }

    @Test
    void skipConfirmationConstructsRuntimeOnlyAfterPreview() throws Exception {
        CountingConfirmation confirmation = new CountingConfirmation(false);
        CountingRuntimeProvider runtimeProvider = new CountingRuntimeProvider();
        ProjectDefenseRunner runner = runner(validScanner(), confirmation, runtimeProvider);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, runner.run(root, false, true, writer(output), writer(new ByteArrayOutputStream())));

        assertEquals(0, confirmation.calls);
        assertEquals(1, runtimeProvider.calls);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Analyzing project with GPT-5.6..."));
    }

    @Test
    void invalidProjectPathUsesExistingSafeExitMapping() {
        ProjectScanner scanner = (path, policy) -> {
            throw new InvalidProjectPathException("safe path failure");
        };
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        ProjectDefenseRunner runner = runner(scanner, new CountingConfirmation(false), new CountingRuntimeProvider());

        assertEquals(ExitCodes.INVALID_PROJECT_PATH, runner.run(root, true, false, writer(new ByteArrayOutputStream()), writer(error)));
        assertEquals("safe path failure" + System.lineSeparator(), error.toString(StandardCharsets.UTF_8));
    }

    private ProjectDefenseRunner runner(ProjectScanner scanner, ConfirmationPrompt confirmation,
            CodeDefenseRuntimeProvider runtimeProvider) {
        return new DefaultProjectDefenseRunner(scanner,
                new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), confirmation,
                new ProjectAnalysisRenderer(), runtimeProvider);
    }

    private ProjectScanner validScanner() throws Exception {
        Path source = root.resolve("App.java");
        Files.writeString(source, "class App {}\n");
        long size = Files.size(source);
        return (path, policy) -> new ScanSummary(root, 1, 0,
                List.of(new SourceFile(source.getFileName(), size)));
    }

    private static PrintWriter writer(ByteArrayOutputStream output) {
        return new PrintWriter(output, true, StandardCharsets.UTF_8);
    }

    private static final class CountingConfirmation implements ConfirmationPrompt {
        private final boolean result;
        private int calls;

        private CountingConfirmation(boolean result) {
            this.result = result;
        }

        @Override
        public boolean confirm(String prompt) {
            calls++;
            return result;
        }
    }

    private static final class CountingRuntimeProvider implements CodeDefenseRuntimeProvider {
        private int calls;

        @Override
        public CodeDefenseRuntime create(PrintWriter output) {
            calls++;
            return new CodeDefenseRuntime(snapshot -> analysis(), ignored -> null,
                    (snapshot, analysis, session) -> {
                        throw new AssertionError("A null interview session must not generate a report");
                    });
        }
    }

    private static ProjectAnalysis analysis() {
        return new ProjectAnalysis("fixture", "Java", "Fixture summary.",
                List.of("Starts the fixture.", "Validates input."),
                List.of(new ProjectComponent("App", "entry-point", "Runs the fixture.", List.of("App.java"))),
                List.of("startup", "validation"),
                List.of(question("one"), question("two"), question("three")));
    }

    private static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(id, "How does " + id + " work?", "Understand " + id,
                List.of("Point one", "Point two"),
                List.of(new CodeEvidence("App.java", 1, 1, "Fixture evidence.")));
    }
}
