package dev.codedefense.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BridgeJsonCodecTest {
    private static final String SECRET = "ANSWER-SECRET-9f31";
    private final BridgeJsonCodec codec = new BridgeJsonCodec();

    @Test
    void roundTripsEveryRequestType() {
        List<BridgeRequest> requests = List.of(
                new BridgeRequest.ConfirmRequest(1, true),
                new BridgeRequest.AnswerRequest(1, SECRET),
                new BridgeRequest.SkipRequest(1),
                new BridgeRequest.CancelRequest(1),
                new ProvenanceConsentRequest(1, SECRET, true));

        for (BridgeRequest request : requests) {
            assertEquals(request, codec.decodeRequest(codec.encodeRequest(request)));
        }
        assertEquals(new BridgeRequest.SkipRequest(2),
                codec.decodeRequest(codec.encodeRequest(new BridgeRequest.SkipRequest(2))));
    }

    @Test
    void provenanceConsentIsStrictAndItsToStringHidesThreadId() {
        ProvenanceConsentRequest request = new ProvenanceConsentRequest(1, SECRET, true);
        assertEquals(request, codec.decodeRequest(codec.encodeRequest(request)));
        assertFalse(request.toString().contains(SECRET));
        assertThrows(BridgeProtocolException.class, () -> codec.decodeRequest(
                "{\"protocolVersion\":1,\"type\":\"provenanceConsent\",\"threadId\":\"id\",\"consent\":true,\"extra\":1}\n"
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> new ProvenanceConsentRequest(1, "id", false));
    }

    @Test
    void roundTripsEveryEventType() {
        List<BridgeEvent> events = List.of(
                new BridgeEvent.HelloEvent(1, List.of("interactiveDefenseV1", "passportStatusV1")),
                new BridgeEvent.PreviewEvent(1, "demo", "Staged change", "balanced", 3, 12, 4),
                new BridgeEvent.ConfirmationRequiredEvent(1,
                        "Send bounded staged change context to the locally authenticated Codex CLI?"),
                new BridgeEvent.QuestionEvent(1, 2, 3, true, "Why is this boundary safe?"),
                new BridgeEvent.EvaluationEvent(1, "PARTIAL", 61, "Good boundary; mention cleanup.",
                        List.of("bounded input"), List.of("cleanup")),
                new BridgeEvent.QuestionScoreEvent(1, 2, 74),
                new BridgeEvent.SummaryEvent(1, List.of(74, 90, 0), 55, "Review needed"),
                new BridgeEvent.PassportSavedEvent(1, "C:/reports/passport.md", "CURRENT", "0123456789ab"),
                new BridgeEvent.CompletedEvent(1, 0, true),
                new BridgeEvent.ErrorEvent(1, "INVALID_REQUEST", "Request is invalid.", 2));

        for (BridgeEvent event : events) {
            assertEquals(event, codec.decodeEvent(codec.encodeEvent(event)));
        }
    }

    @Test
    void preservesProtocolOneQuestionGoldenJsonWithoutEvidence() {
        byte[] encoded = codec.encodeEvent(new BridgeEvent.QuestionEvent(1, 1, 3, false, "Explain it"));

        assertEquals("{\"protocolVersion\":1,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":false,\"prompt\":\"Explain it\"}\n",
                new String(encoded, StandardCharsets.UTF_8));
        assertEquals(new BridgeEvent.QuestionEvent(1, 1, 3, false, "Explain it"),
                codec.decodeEvent(encoded));
    }

    @Test
    void protocolTwoQuestionHasStrictReasonFreeEvidenceShape() {
        BridgeEvent.QuestionEvent question = new BridgeEvent.QuestionEvent(2, 1, 3, false, "Explain it",
                List.of(new BridgeEvidenceLocation("src/A.java", 4, 9)));

        byte[] encoded = codec.encodeEvent(question);

        assertEquals("{\"protocolVersion\":2,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":false,\"prompt\":\"Explain it\",\"evidence\":[{\"relativePath\":"
                + "\"src/A.java\",\"startLine\":4,\"endLine\":9}]}\n",
                new String(encoded, StandardCharsets.UTF_8));
        assertEquals(question, codec.decodeEvent(encoded));
        assertFalse(new String(encoded, StandardCharsets.UTF_8).contains("reason"));
    }

    @Test
    void protocolTwoFollowUpRequiresExplicitEmptyEvidence() {
        String json = "{\"protocolVersion\":2,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":true,\"prompt\":\"One more detail?\",\"evidence\":[]}\n";

        assertEquals(new BridgeEvent.QuestionEvent(2, 1, 3, true, "One more detail?", List.of()),
                codec.decodeEvent(bytes(json)));
        assertThrows(BridgeProtocolException.class, () -> codec.decodeEvent(bytes(json.replace(",\"evidence\":[]", ""))));
    }

    @Test
    void rejectsMalformedProtocolTwoEvidence() {
        String prefix = "{\"protocolVersion\":2,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":false,\"prompt\":\"Explain\",\"evidence\":";
        assertThrows(BridgeProtocolException.class, () -> codec.decodeEvent(bytes(prefix + "[]}\n")));
        assertThrows(BridgeProtocolException.class, () -> codec.decodeEvent(bytes(prefix
                + "[{\"relativePath\":\"src/A.java\",\"startLine\":1,\"endLine\":1,\"reason\":\"secret\"}]}\n")));
        assertThrows(BridgeProtocolException.class, () -> codec.decodeEvent(bytes(prefix
                + "[{\"relativePath\":\"../A.java\",\"startLine\":1,\"endLine\":1}]}\n")));
        assertThrows(BridgeProtocolException.class, () -> codec.decodeEvent(bytes(prefix
                + "[{\"relativePath\":\"src/A.java\",\"startLine\":2,\"endLine\":1}]}\n")));
        assertThrows(BridgeProtocolException.class, () -> codec.decodeEvent(bytes(prefix
                + "[{\"relativePath\":\"src/A.java\",\"relativePath\":\"src/B.java\","
                + "\"startLine\":1,\"endLine\":1}]}\n")));
        assertThrows(BridgeProtocolException.class, () -> codec.decodeEvent(bytes(
                "{\"protocolVersion\":1,\"type\":\"question\",\"number\":1,\"total\":3,"
                        + "\"followUp\":false,\"prompt\":\"Explain\",\"evidence\":[]}\n")));
    }

    @Test
    void encodingIsDeterministicSingleLineUtf8WithLfTermination() {
        BridgeRequest request = new BridgeRequest.AnswerRequest(1, "Привет 👋");

        byte[] first = codec.encodeRequest(request);
        byte[] second = codec.encodeRequest(request);
        String json = new String(first, StandardCharsets.UTF_8);

        assertArrayEquals(first, second);
        assertEquals("{\"protocolVersion\":1,\"type\":\"answer\",\"answer\":\"Привет 👋\"}\n", json);
        assertFalse(json.substring(0, json.length() - 1).contains("\n"));
        assertFalse(json.contains("\r"));
    }

    @Test
    void rejectsMalformedUtf8WithoutEchoingInput() {
        byte[] malformed = new byte[] {'{', (byte) 0xC3, '}', '\n'};

        BridgeProtocolException exception = assertThrows(BridgeProtocolException.class,
                () -> codec.decodeRequest(malformed));

        assertEquals("Bridge message is not valid UTF-8.", exception.getMessage());
        assertFalse(exception.getMessage().contains("�"));
    }

    @Test
    void rejectsUnknownRequestTypeAndField() {
        assertSafeFailure("{\"protocolVersion\":1,\"type\":\"launchMissiles\"}\n");
        assertSafeFailure("{\"protocolVersion\":1,\"type\":\"skip\",\"secret\":\"" + SECRET + "\"}\n");
    }

    @Test
    void rejectsDuplicateKeysFractionalIntegersAndTrailingTokens() {
        assertSafeFailure("{\"protocolVersion\":1,\"protocolVersion\":1,\"type\":\"skip\"}\n");
        assertSafeFailure("{\"protocolVersion\":1.0,\"type\":\"skip\"}\n");
        assertSafeFailure("{\"protocolVersion\":1,\"type\":\"skip\"} {}\n");
    }

    @Test
    void rejectsUnsupportedProtocolVersion() {
        BridgeProtocolException exception = assertThrows(BridgeProtocolException.class,
                () -> codec.decodeRequest(bytes("{\"protocolVersion\":4,\"type\":\"skip\"}\n")));

        assertEquals("Unsupported bridge protocol version.", exception.getMessage());
    }

    @Test
    void enforcesMaximumLineSizeAndTheStricterAnswerLimit() {
        String oversized = "x".repeat(BridgeProtocol.MAX_LINE_BYTES);
        assertThrows(BridgeProtocolException.class,
                () -> codec.decodeRequest(bytes(oversized)));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeRequest.AnswerRequest(1, oversized));
    }

    @Test
    void validatesCapabilitiesAndCopiesCollections() {
        var capabilities = new java.util.ArrayList<>(List.of("interactiveDefenseV1"));
        BridgeEvent.HelloEvent event = new BridgeEvent.HelloEvent(1, capabilities);
        capabilities.add("codexProvenanceV1");

        assertEquals(List.of("interactiveDefenseV1"), event.capabilities());
        assertThrows(UnsupportedOperationException.class,
                () -> event.capabilities().add("another"));
        assertThrows(IllegalArgumentException.class,
                () -> new BridgeEvent.HelloEvent(1, List.of("codexProvenanceV1", "codexProvenanceV1")));
    }

    @Test
    void sensitiveToStringsExposeOnlyMetadata() {
        String answerText = new BridgeRequest.AnswerRequest(1, SECRET).toString();
        String questionText = new BridgeEvent.QuestionEvent(1, 1, 3, false, SECRET).toString();
        String evaluationText = new BridgeEvent.EvaluationEvent(1, "PARTIAL", 61, SECRET,
                List.of(SECRET), List.of(SECRET)).toString();

        assertFalse(answerText.contains(SECRET));
        assertFalse(questionText.contains(SECRET));
        assertFalse(evaluationText.contains(SECRET));
        assertTrue(answerText.contains("length=" + SECRET.length()));
        assertTrue(questionText.contains("promptLength=" + SECRET.length()));
        assertTrue(evaluationText.contains("feedbackLength=" + SECRET.length()));
    }

    private void assertSafeFailure(String json) {
        BridgeProtocolException exception = assertThrows(BridgeProtocolException.class,
                () -> codec.decodeRequest(bytes(json)));
        assertFalse(exception.getMessage().contains(SECRET));
        assertFalse(exception.getMessage().contains(json));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
