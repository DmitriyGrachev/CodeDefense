package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.application.ShowLatestReportUseCase;
import dev.codedefense.report.ReportPersistenceException;
import dev.codedefense.report.ReportStore;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ReportCommandTest {
    @Test
    void printsLatestMarkdownWithExactlyOneFinalNewline() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(Optional.of("# Latest report\n"), output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute());
        assertEquals("# Latest report\n", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void printsTheFixedNoReportMessage() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, commandLine(Optional.empty(), output, new ByteArrayOutputStream()).execute());
        assertEquals("No completed CodeDefense report is available yet.\n", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void mapsReadFailureToExitNineWithoutAStackTrace() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(new ReportStore() {
            @Override public dev.codedefense.domain.SavedReport save(dev.codedefense.domain.FinalReport report) { throw new UnsupportedOperationException(); }
            @Override public Optional<String> readLatest() { throw ReportPersistenceException.readFailure(); }
        }, new ByteArrayOutputStream(), error);

        assertEquals(ExitCodes.REPORT_PERSISTENCE_FAILED, commandLine.execute());
        assertTrue(error.toString(StandardCharsets.UTF_8).contains(ReportPersistenceException.READ_FAILURE_MESSAGE));
        assertFalse(error.toString(StandardCharsets.UTF_8).contains("\tat "));
    }

    @Test
    void helpDoesNotReadTheStore() {
        ReportStore store = new ReportStore() {
            @Override public dev.codedefense.domain.SavedReport save(dev.codedefense.domain.FinalReport report) { throw new UnsupportedOperationException(); }
            @Override public Optional<String> readLatest() { throw new AssertionError("help must not read reports"); }
        };

        assertEquals(ExitCodes.SUCCESS, commandLine(store, new ByteArrayOutputStream(), new ByteArrayOutputStream()).execute("--help"));
    }

    private CommandLine commandLine(Optional<String> latest, ByteArrayOutputStream output, ByteArrayOutputStream error) {
        return commandLine(new ReportStore() {
            @Override public dev.codedefense.domain.SavedReport save(dev.codedefense.domain.FinalReport report) { throw new UnsupportedOperationException(); }
            @Override public Optional<String> readLatest() { return latest; }
        }, output, error);
    }

    private CommandLine commandLine(ReportStore store, ByteArrayOutputStream output, ByteArrayOutputStream error) {
        CommandLine line = new CommandLine(new ReportCommand(new ShowLatestReportUseCase(store)));
        line.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        line.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));
        return line;
    }
}
