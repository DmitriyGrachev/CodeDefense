package dev.codedefense.bridge;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Messages accepted from an IDE adapter. */
public sealed interface BridgeRequest permits BridgeRequest.ConfirmRequest, BridgeRequest.AnswerRequest,
        BridgeRequest.SkipRequest, BridgeRequest.CancelRequest {
    int protocolVersion();

    record ConfirmRequest(int protocolVersion, boolean accepted) implements BridgeRequest {
        public ConfirmRequest {
            BridgeProtocol.requireVersion(protocolVersion);
        }
    }

    record AnswerRequest(int protocolVersion, String answer) implements BridgeRequest {
        public AnswerRequest {
            BridgeProtocol.requireVersion(protocolVersion);
            Objects.requireNonNull(answer, "answer");
            if (answer.getBytes(StandardCharsets.UTF_8).length > BridgeProtocol.MAX_ANSWER_BYTES) {
                throw new IllegalArgumentException("answer exceeds the bridge limit");
            }
        }

        @Override
        public String toString() {
            return "AnswerRequest[protocolVersion=" + protocolVersion + ", length=" + answer.length() + "]";
        }
    }

    record SkipRequest(int protocolVersion) implements BridgeRequest {
        public SkipRequest {
            BridgeProtocol.requireVersion(protocolVersion);
        }
    }

    record CancelRequest(int protocolVersion) implements BridgeRequest {
        public CancelRequest {
            BridgeProtocol.requireVersion(protocolVersion);
        }
    }
}
