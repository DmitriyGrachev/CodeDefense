package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.domain.FinalReport;
import dev.codedefense.domain.SavedReport;
import dev.codedefense.report.ReportStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShowLatestReportUseCaseTest {
    @Test
    void delegatesDirectlyToStore() {
        RuntimeException failure = new IllegalStateException("read failed");
        ReportStore store = new ReportStore() {
            @Override public SavedReport save(FinalReport report) { throw new UnsupportedOperationException(); }
            @Override public Optional<String> readLatest() { throw failure; }
        };

        ShowLatestReportUseCase useCase = new ShowLatestReportUseCase(store);

        assertSame(failure, assertThrows(IllegalStateException.class, useCase::showLatest));
    }

    @Test
    void returnsStoreContentWithoutTransformation() {
        Optional<String> expected = Optional.of("# latest\n");
        ReportStore store = new ReportStore() {
            @Override public SavedReport save(FinalReport report) { throw new UnsupportedOperationException(); }
            @Override public Optional<String> readLatest() { return expected; }
        };

        assertEquals(expected, new ShowLatestReportUseCase(store).showLatest());
    }
}
