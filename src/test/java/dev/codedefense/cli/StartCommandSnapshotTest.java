package dev.codedefense.cli;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.terminal.ConfirmationPrompt;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartCommandSnapshotTest {
    @TempDir
    Path root;

    @Test
    void dryRunNeverCallsConfirmation() throws Exception {
        var confirmation = new CountingConfirmation(false);
        var commandLine = commandLine(validScanner(), confirmation, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--dry-run", root.toString()));
        assertEquals(0, confirmation.calls);
    }

    @Test
    void yesBypassesConfirmation() throws Exception {
        var confirmation = new CountingConfirmation(false);
        var commandLine = commandLine(validScanner(), confirmation, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--yes", root.toString()));
        assertEquals(0, confirmation.calls);
    }

    @Test
    void declinePrintsCancellationMessage() throws Exception {
        var output = new ByteArrayOutputStream();
        var commandLine = commandLine(validScanner(), new CountingConfirmation(false), output);

        assertEquals(ExitCodes.SUCCESS, commandLine.execute(root.toString()));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Cancelled before any source content was sent."));
    }

    @Test
    void emptySnapshotMapsToNoSupportedFilesExitCode() {
        ProjectScanner scanner = (path, policy) -> new ScanSummary(root, 1, 0, List.of(new SourceFile(Path.of("missing.java"), 1)));
        var commandLine = commandLine(scanner, new CountingConfirmation(false), new ByteArrayOutputStream());

        assertEquals(ExitCodes.NO_SUPPORTED_SOURCE_FILES, commandLine.execute("--dry-run", root.toString()));
    }

    private CommandLine commandLine(ProjectScanner scanner, ConfirmationPrompt confirmation, ByteArrayOutputStream output) {
        var commandLine = new CommandLine(new StartCommand(scanner, new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), confirmation));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        return commandLine;
    }

    private ProjectScanner validScanner() throws Exception {
        Path file = root.resolve("App.java");
        Files.writeString(file, "class App {}");
        long fileSize = Files.size(file);
        return (path, policy) -> new ScanSummary(root, 1, 0, List.of(new SourceFile(file.getFileName(), fileSize)));
    }

    private static final class CountingConfirmation implements ConfirmationPrompt {
        private final boolean answer;
        private int calls;
        private CountingConfirmation(boolean answer) { this.answer = answer; }
        @Override public boolean confirm(String prompt) { calls++; return answer; }
    }
}
