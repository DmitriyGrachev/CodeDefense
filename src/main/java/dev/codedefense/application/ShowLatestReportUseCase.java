package dev.codedefense.application;

import dev.codedefense.report.ReportStore;
import java.util.Objects;
import java.util.Optional;

/** Retrieves the latest saved report without presentation or error translation. */
public final class ShowLatestReportUseCase {
    private final ReportStore reportStore;

    public ShowLatestReportUseCase(ReportStore reportStore) {
        this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
    }

    public Optional<String> showLatest() {
        return reportStore.readLatest();
    }
}
