package dev.codedefense.jetbrains.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BridgeLineCodecTest {
    private final BridgeLineCodec codec = new BridgeLineCodec();

    @Test
    void decodesBoundedProtocolOneEvent() {
        var event = codec.decodeEvent(bytes("{\"protocolVersion\":1,\"type\":\"completed\","
                + "\"exitCode\":0,\"codexInvoked\":false}\n"));

        assertEquals("completed", event.type());
        assertEquals(0, event.integer("exitCode"));
        assertFalse(event.bool("codexInvoked"));
    }

    @Test
    void decodesProtocolTwoQuestionIntoImmutableTypedEvidence() {
        var event = codec.decodeEvent(bytes("{\"protocolVersion\":2,\"type\":\"question\","
                + "\"number\":1,\"total\":3,\"followUp\":false,\"prompt\":\"Explain it\","
                + "\"evidence\":[{\"relativePath\":\"src/A.java\",\"startLine\":4,\"endLine\":9},"
                + "{\"relativePath\":\"src/B.java\",\"startLine\":12,\"endLine\":12}]}\n"));

        List<EvidenceLocationView> evidence = event.evidence();
        assertIterableEquals(List.of(
                new EvidenceLocationView("src/A.java", 4, 9),
                new EvidenceLocationView("src/B.java", 12, 12)), evidence);
        assertThrows(UnsupportedOperationException.class,
                () -> evidence.add(new EvidenceLocationView("src/C.java", 1, 1)));
        assertFalse(event.toString().contains("Explain it"));
        assertFalse(event.toString().contains("protocolVersion"));
    }

    @Test
    void protocolTwoQuestionRequiresExactSafeEvidenceShape() {
        assertInvalid(question2(""));
        assertInvalid(question2("\"evidence\":[]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src/A.java\","
                + "\"startLine\":1,\"endLine\":1,\"reason\":\"private\"}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src/A.java\","
                + "\"startLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"../A.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"C:/A.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"/src/A.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\" src/A.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src/A.java \","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src/A:Stream.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src/\\u0001A.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src\\\\A.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src/A.java\","
                + "\"startLine\":0,\"endLine\":1}]"));
        assertInvalid(question2("\"evidence\":[{\"relativePath\":\"src/A.java\","
                + "\"startLine\":5,\"endLine\":4}]"));
        String item = "{\"relativePath\":\"src/A.java\",\"startLine\":1,\"endLine\":1}";
        assertInvalid(question2("\"evidence\":[" + item + "," + item + "]"));
        String eleven = IntStream.range(0, 11)
                .mapToObj(index -> "{\"relativePath\":\"src/A" + index + ".java\","
                        + "\"startLine\":1,\"endLine\":1}")
                .collect(java.util.stream.Collectors.joining(","));
        assertInvalid(question2("\"evidence\":[" + eleven + "]"));
    }

    @Test
    void protocolTwoFollowUpRequiresAnExplicitEmptyEvidenceArray() {
        String valid = "{\"protocolVersion\":2,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":true,\"prompt\":\"Clarify\",\"evidence\":[]}\n";
        assertTrue(codec.decodeEvent(bytes(valid)).evidence().isEmpty());

        assertInvalid(valid.replace(",\"evidence\":[]", ""));
        assertInvalid(valid.replace("[]", "[{\"relativePath\":\"src/A.java\","
                + "\"startLine\":1,\"endLine\":1}]"));
    }

    @Test
    void protocolOneQuestionKeepsTheLegacyExactShape() {
        var legacy = codec.decodeEvent(bytes("{\"protocolVersion\":1,\"type\":\"question\","
                + "\"number\":1,\"total\":3,\"followUp\":false,\"prompt\":\"Explain\"}\n"));
        assertTrue(legacy.evidence().isEmpty());
        assertInvalid("{\"protocolVersion\":1,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":false,\"prompt\":\"Explain\",\"evidence\":[]}\n");
    }

    @Test
    void acceptsProtocolTwoForNonQuestionEventsWithoutChangingTheirShape() {
        var completed = codec.decodeEvent(bytes("{\"protocolVersion\":2,\"type\":\"completed\","
                + "\"exitCode\":0,\"codexInvoked\":false}\n"));
        assertEquals("completed", completed.type());
        assertTrue(completed.evidence().isEmpty());
        assertInvalid("{\"protocolVersion\":2,\"type\":\"completed\",\"exitCode\":0,"
                + "\"codexInvoked\":false,\"evidence\":[]}\n");
    }

    @Test
    void evidenceLocationDiagnosticsContainOnlyPortablePathAndRange() {
        String secret = "PRIVATE-RAW-JSON";
        var location = new EvidenceLocationView("src/" + secret + ".java", 4, 9);
        String diagnostic = location.toString();

        assertTrue(diagnostic.contains("src/" + secret + ".java"));
        assertTrue(diagnostic.contains("startLine=4"));
        assertTrue(diagnostic.contains("endLine=9"));
        assertFalse(diagnostic.contains("prompt"));
        assertFalse(diagnostic.contains("reason"));
        assertFalse(diagnostic.contains("{"));
    }

    @Test
    void writesDeterministicRequestsAndKeepsAnswersOutOfDiagnostics() {
        String marker = "PRIVATE-ANSWER-5c42";
        String json = new String(codec.answerRequest(marker), StandardCharsets.UTF_8);

        assertEquals("{\"protocolVersion\":2,\"type\":\"answer\",\"answer\":\"" + marker + "\"}\n", json);
        assertFalse(codec.toString().contains(marker));
    }

    @Test
    void writesEphemeralProvenanceConsentWithoutExposingItInDiagnostics() {
        String threadId = "private-thread-id";
        String json = new String(codec.provenanceConsentRequest(threadId, true), StandardCharsets.UTF_8);

        assertEquals("{\"protocolVersion\":2,\"type\":\"provenanceConsent\","
                + "\"threadId\":\"private-thread-id\",\"consent\":true}\n", json);
        assertFalse(codec.toString().contains(threadId));
        assertThrows(BridgeTransportException.class,
                () -> codec.provenanceConsentRequest(threadId, false));
    }

    @Test
    void explicitProtocolOneCodecKeepsFallbackRequestsCompatible() {
        BridgeLineCodec legacy = new BridgeLineCodec(1);

        assertEquals("{\"protocolVersion\":1,\"type\":\"skip\"}\n",
                new String(legacy.skipRequest(), StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> new BridgeLineCodec(0));
        assertThrows(IllegalArgumentException.class, () -> new BridgeLineCodec(3));
    }

    @Test
    void rejectsMalformedUtf8DuplicateKeysTrailingTokensAndOversizedLines() {
        assertThrows(BridgeTransportException.class,
                () -> codec.decodeEvent(new byte[] {'{', (byte) 0xC3, '}', '\n'}));
        assertThrows(BridgeTransportException.class,
                () -> codec.decodeEvent(bytes("{\"protocolVersion\":1,\"type\":\"completed\","
                        + "\"type\":\"error\"}\n")));
        assertThrows(BridgeTransportException.class,
                () -> codec.decodeEvent(bytes("{\"protocolVersion\":1,\"type\":\"completed\"} {}\n")));
        assertThrows(BridgeTransportException.class,
                () -> codec.decodeEvent(bytes("x".repeat(BridgeLineCodec.MAX_LINE_BYTES + 1))));
        assertThrows(BridgeTransportException.class,
                () -> codec.decodeEvent(bytes("{\"protocolVersion\":1,\"type\":\"sourceSnapshot\"}\n")));
        assertThrows(BridgeTransportException.class,
                () -> codec.decodeEvent(bytes("{\"protocolVersion\":1,\"type\":\"completed\","
                        + "\"exitCode\":0,\"codexInvoked\":false,\"source\":\"private\"}\n")));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String question2(String evidenceField) {
        String suffix = evidenceField.isEmpty() ? "" : "," + evidenceField;
        return "{\"protocolVersion\":2,\"type\":\"question\",\"number\":1,\"total\":3,"
                + "\"followUp\":false,\"prompt\":\"Explain\"" + suffix + "}\n";
    }

    private void assertInvalid(String json) {
        assertThrows(BridgeTransportException.class, () -> codec.decodeEvent(bytes(json)));
    }
}
