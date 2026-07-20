package dev.codedefense.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.codedefense.domain.EvidenceCoverageHunk;
import dev.codedefense.domain.EvidenceCoverageMap;
import dev.codedefense.domain.EvidenceCoverageState;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BridgeEvidenceCoverageTest {
    @Test
    void protocolThreePublishesBoundedCumulativeCoverage() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BridgeSession session = new BridgeSession(new ByteArrayInputStream(new byte[0]), output,
                BridgeProtocol.VERSION_3);
        BridgeEvidenceCoveragePublisher publisher = new BridgeEvidenceCoveragePublisher(session);
        publisher.prepare(new EvidenceCoverageMap("a".repeat(64), List.of(
                new EvidenceCoverageHunk("src/App.java", 1, 10, 10, true,
                        EvidenceCoverageState.REFERENCED, List.of("decision")),
                new EvidenceCoverageHunk("src/App.java", 2, 30, 30, true,
                        EvidenceCoverageState.REFERENCED, List.of("test-prediction")))));

        publisher.publish("decision");
        publisher.publish("test-prediction");

        String[] lines = output.toString(StandardCharsets.UTF_8).strip().split("\\n");
        BridgeJsonCodec codec = new BridgeJsonCodec();
        BridgeEvent.CoverageEvent first = (BridgeEvent.CoverageEvent) codec.decodeEvent(
                (lines[0] + "\n").getBytes(StandardCharsets.UTF_8));
        BridgeEvent.CoverageEvent last = (BridgeEvent.CoverageEvent) codec.decodeEvent(
                (lines[1] + "\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(1, first.referencedHunks());
        assertEquals(2, last.referencedHunks());
        assertEquals(2, last.hunks().size());
        assertEquals("Evidence use only — not correctness or safety coverage.", last.disclaimer());
        assertFalse(lines[1].contains("source"));
        assertFalse(lines[1].contains("prompt"));
    }
}
