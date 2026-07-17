package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.FinalReport;
import dev.codedefense.domain.NarrativeSource;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.ReportGenerationRequest;
import dev.codedefense.domain.ReportMetadata;
import dev.codedefense.domain.ReportNarrative;
import dev.codedefense.domain.SavedReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnderstandingReportServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T12:34:56Z"), ZoneOffset.UTC);

    @Test
    void generatesOnceWithFixedMetadataAndSavesAiNarrative() {
        ReportGenerationRequest request = Fixtures.request();
        ProjectSnapshot snapshot = snapshot();
        ReportNarrative narrative = Fixtures.narrative();
        CapturingGenerator generator = new CapturingGenerator(narrative);
        CapturingStore store = new CapturingStore();

        SavedReport saved = service(generator, store).generateAndSave(snapshot, request.analysis(), request.session());

        assertEquals(1, generator.calls);
        assertEquals(CLOCK.instant(), generator.metadata.analyzedAt());
        assertEquals("test-model", generator.metadata.model());
        assertEquals(snapshot.promptBytes(), generator.metadata.snapshotBytes());
        assertFalse(generator.metadata.toString().contains("SNAPSHOT_PROMPT_SECRET"));
        assertEquals(NarrativeSource.AI, store.saved.narrativeSource());
        assertSame(narrative, store.saved.narrative());
        assertEquals(store.result, saved);
    }

    @Test
    void propagatesNonAvailabilityCodexFailuresWithoutSaving() {
        ReportGenerationRequest request = Fixtures.request();
        for (RuntimeException failure : new RuntimeException[] {
                new CodexTimeoutException(), new CodexExecutionException(1, "failed"),
                new InvalidCodexResponseException("invalid") }) {
            CapturingGenerator generator = new CapturingGenerator(failure);
            CapturingStore store = new CapturingStore();

            assertThrows(failure.getClass(),
                    () -> service(generator, store).generateAndSave(snapshot(), request.analysis(), request.session()));

            assertEquals(1, generator.calls);
            assertEquals(0, store.calls);
        }
    }

    @Test
    void usesFallbackAndStoresOnceWhenCodexIsNotInstalled() {
        assertFallback(Fixtures.request(), new CodexNotInstalledException());
    }

    @Test
    void usesFallbackAndStoresOnceWhenCodexIsNotAuthenticated() {
        assertFallback(Fixtures.request(), new CodexNotAuthenticatedException());
    }

    @Test
    void propagatesInterruptionWithoutSaving() {
        ReportGenerationRequest request = Fixtures.request();
        CapturingGenerator generator = new CapturingGenerator(new CodexInterruptedException(new InterruptedException()));
        CapturingStore store = new CapturingStore();

        assertThrows(CodexInterruptedException.class,
                () -> service(generator, store).generateAndSave(snapshot(), request.analysis(), request.session()));

        assertEquals(1, generator.calls);
        assertEquals(0, store.calls);
    }

    @Test
    void propagatesUnexpectedRuntimeFailureWithoutSaving() {
        ReportGenerationRequest request = Fixtures.request();
        CapturingGenerator generator = new CapturingGenerator(new IllegalStateException("unexpected"));
        CapturingStore store = new CapturingStore();

        assertThrows(IllegalStateException.class,
                () -> service(generator, store).generateAndSave(snapshot(), request.analysis(), request.session()));

        assertEquals(1, generator.calls);
        assertEquals(0, store.calls);
    }

    @Test
    void propagatesPersistenceFailureAfterSingleSuccessfulGeneration() {
        ReportGenerationRequest request = Fixtures.request();
        CapturingGenerator generator = new CapturingGenerator(Fixtures.narrative());
        CapturingStore store = new CapturingStore();
        store.failure = ReportPersistenceException.saveFailure();

        assertThrows(ReportPersistenceException.class,
                () -> service(generator, store).generateAndSave(snapshot(), request.analysis(), request.session()));

        assertEquals(1, generator.calls);
        assertEquals(1, store.calls);
    }

    private static UnderstandingReportService service(ReportNarrativeGenerator generator, CapturingStore store) {
        return new UnderstandingReportService(generator, new DeterministicReportNarrativeFactory(), store, CLOCK, "test-model");
    }

    private static void assertFallback(ReportGenerationRequest request, RuntimeException failure) {
        CapturingGenerator generator = new CapturingGenerator(failure);
        CapturingStore store = new CapturingStore();

        SavedReport saved = service(generator, store).generateAndSave(snapshot(), request.analysis(), request.session());

        assertEquals(1, generator.calls);
        assertEquals(1, store.calls);
        assertEquals(NarrativeSource.DETERMINISTIC_FALLBACK, store.saved.narrativeSource());
        assertEquals(NarrativeSource.DETERMINISTIC_FALLBACK, saved.narrativeSource());
    }

    private static ProjectSnapshot snapshot() {
        String prompt = "SNAPSHOT_PROMPT_SECRET";
        return new ProjectSnapshot(Path.of("project"), "project-name", "Java CLI",
                new dev.codedefense.domain.ScanSummary(Path.of("project"), 1, 0, java.util.List.of(new dev.codedefense.domain.SourceFile(Path.of("src/App.java")))),
                java.util.List.of(new ProjectSnapshot.SelectedFile(Path.of("src/App.java"), 2, false, 20)),
                prompt, prompt.getBytes(StandardCharsets.UTF_8).length, 0);
    }

    private static final class CapturingGenerator implements ReportNarrativeGenerator {
        private final ReportNarrative narrative;
        private final RuntimeException failure;
        private int calls;
        private ReportMetadata metadata;

        CapturingGenerator(ReportNarrative narrative) { this.narrative = narrative; this.failure = null; }
        CapturingGenerator(RuntimeException failure) { this.narrative = null; this.failure = failure; }

        @Override public ReportNarrative generate(ReportGenerationRequest request, ReportMetadata metadata) {
            calls++;
            this.metadata = metadata;
            if (failure != null) throw failure;
            return narrative;
        }
    }

    private static final class CapturingStore implements ReportStore {
        private SavedReport result;
        private int calls;
        private FinalReport saved;
        private RuntimeException failure;

        @Override public SavedReport save(FinalReport report) {
            calls++;
            saved = report;
            if (failure != null) throw failure;
            result = new SavedReport(Path.of("C:/reports/latest.md"), report.narrativeSource());
            return result;
        }
        @Override public Optional<String> readLatest() { return Optional.empty(); }
    }
}
