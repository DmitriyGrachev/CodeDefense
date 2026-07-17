package dev.codedefense.report;

import dev.codedefense.domain.FinalReport;
import dev.codedefense.domain.SavedReport;
import java.util.Optional;

/** Persistence boundary for rendered understanding reports. */
public interface ReportStore {
    SavedReport save(FinalReport report);

    Optional<String> readLatest();
}
