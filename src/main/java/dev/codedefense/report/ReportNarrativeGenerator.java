package dev.codedefense.report;

import dev.codedefense.domain.ReportGenerationRequest;
import dev.codedefense.domain.ReportMetadata;
import dev.codedefense.domain.ReportNarrative;

/** Boundary for generating the optional narrative portion of an understanding report. */
public interface ReportNarrativeGenerator {
    ReportNarrative generate(ReportGenerationRequest request, ReportMetadata metadata);
}
