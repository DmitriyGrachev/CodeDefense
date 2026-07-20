package dev.codedefense.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.TechnicalQuestion;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BridgeInterviewOutputTest {
    private static final String PRIVATE_REASON = "PRIVATE-EVIDENCE-REASON";
    private final BridgeJsonCodec codec = new BridgeJsonCodec();

    @Test
    void protocolTwoMapsPrimaryEvidenceToSortedDistinctReasonFreeLocations() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BridgeInterviewOutput output = output(2, bytes);
        TechnicalQuestion question = question(List.of(
                new CodeEvidence("src\\B.java", 8, 10, PRIVATE_REASON),
                new CodeEvidence("src/A.java", 2, 4, PRIVATE_REASON),
                new CodeEvidence("src/A.java", 2, 4, PRIVATE_REASON)));

        output.renderPrimaryQuestion(1, 3, question);

        BridgeEvent.QuestionEvent event = assertInstanceOf(BridgeEvent.QuestionEvent.class, event(bytes));
        assertEquals(2, event.protocolVersion());
        assertEquals(List.of(
                new BridgeEvidenceLocation("src/A.java", 2, 4),
                new BridgeEvidenceLocation("src/B.java", 8, 10)), event.evidence());
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains(PRIVATE_REASON));
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("expected"));
    }

    @Test
    void protocolTwoFollowUpEmitsNoNewEvidence() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BridgeInterviewOutput output = output(2, bytes);
        output.renderPrimaryQuestion(1, 3, question(List.of(
                new CodeEvidence("src/A.java", 2, 4, PRIVATE_REASON))));
        bytes.reset();

        output.renderFollowUp("Can you clarify?");

        BridgeEvent.QuestionEvent event = assertInstanceOf(BridgeEvent.QuestionEvent.class, event(bytes));
        assertEquals(List.of(), event.evidence());
        assertEquals(true, event.followUp());
    }

    @Test
    void protocolOneRetainsExactQuestionShape() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BridgeInterviewOutput output = output(1, bytes);

        output.renderPrimaryQuestion(1, 3, question(List.of(
                new CodeEvidence("src/A.java", 2, 4, PRIVATE_REASON))));

        assertEquals("{\"protocolVersion\":1,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":false,\"prompt\":\"Explain the change.\"}\n",
                bytes.toString(StandardCharsets.UTF_8));
    }

    private BridgeInterviewOutput output(int protocolVersion, ByteArrayOutputStream bytes) {
        return new BridgeInterviewOutput(new BridgeSession(
                new ByteArrayInputStream(new byte[0]), bytes, protocolVersion));
    }

    private BridgeEvent event(ByteArrayOutputStream bytes) {
        return codec.decodeEvent(bytes.toByteArray());
    }

    private TechnicalQuestion question(List<CodeEvidence> evidence) {
        return new TechnicalQuestion("decision", "Explain the change.", "Understand it",
                List.of("first", "second"), evidence);
    }
}
