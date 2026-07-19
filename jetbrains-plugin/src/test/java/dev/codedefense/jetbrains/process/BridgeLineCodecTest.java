package dev.codedefense.jetbrains.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
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
    void writesDeterministicRequestsAndKeepsAnswersOutOfDiagnostics() {
        String marker = "PRIVATE-ANSWER-5c42";
        String json = new String(codec.answerRequest(marker), StandardCharsets.UTF_8);

        assertEquals("{\"protocolVersion\":1,\"type\":\"answer\",\"answer\":\"" + marker + "\"}\n", json);
        assertFalse(codec.toString().contains(marker));
    }

    @Test
    void writesEphemeralProvenanceConsentWithoutExposingItInDiagnostics() {
        String threadId = "private-thread-id";
        String json = new String(codec.provenanceConsentRequest(threadId, true), StandardCharsets.UTF_8);

        assertEquals("{\"protocolVersion\":1,\"type\":\"provenanceConsent\","
                + "\"threadId\":\"private-thread-id\",\"consent\":true}\n", json);
        assertFalse(codec.toString().contains(threadId));
        assertThrows(BridgeTransportException.class,
                () -> codec.provenanceConsentRequest(threadId, false));
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
}
