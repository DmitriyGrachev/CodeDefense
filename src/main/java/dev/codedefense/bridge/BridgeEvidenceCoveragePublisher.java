package dev.codedefense.bridge;

import dev.codedefense.domain.EvidenceCoverageMap;
import java.util.Objects;

public final class BridgeEvidenceCoveragePublisher {
    public static final String DISCLAIMER =
            "Evidence use only — not correctness or safety coverage.";
    private final BridgeSession session;
    private EvidenceCoverageMap coverage;

    public BridgeEvidenceCoveragePublisher(BridgeSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public void prepare(EvidenceCoverageMap coverage) {
        this.coverage = Objects.requireNonNull(coverage, "coverage");
    }

    public void publish(String questionId) {
        if (session.protocolVersion() < BridgeProtocol.VERSION_3 || coverage == null) return;
        EvidenceCoverageMap visible = coverage.cumulativeThrough(questionId);
        var summary = visible.summary();
        session.emit(new BridgeEvent.CoverageEvent(BridgeProtocol.VERSION_3,
                summary.totalHunks(), summary.measurableHunks(), summary.referencedHunks(),
                visible.hunks().stream().map(hunk -> new BridgeCoverageHunk(
                        hunk.relativePath(), hunk.ordinal(), hunk.startLine(), hunk.endLine(), hunk.navigable(),
                        hunk.state().name(), hunk.categoryIds())).toList(), DISCLAIMER));
    }
}
