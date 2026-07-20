package dev.codedefense.bridge;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ProvenanceConsentRequest(int protocolVersion, String threadId,
        boolean consent) implements BridgeRequest {
    public ProvenanceConsentRequest {
        BridgeProtocol.requireVersion(protocolVersion);
        Objects.requireNonNull(threadId, "threadId");
        int bytes = threadId.getBytes(StandardCharsets.UTF_8).length;
        if (threadId.isBlank() || bytes > 4_096 || threadId.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("thread ID is invalid");
        }
        if (!consent) throw new IllegalArgumentException("explicit provenance consent is required");
    }

    @Override public String toString() {
        return "ProvenanceConsentRequest[protocolVersion=%d, threadIdLength=%d, consent=%s]"
                .formatted(protocolVersion, threadId.length(), consent);
    }
}
