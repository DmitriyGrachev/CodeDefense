package dev.codedefense.bridge;

import dev.codedefense.terminal.ConfirmationPrompt;
import java.util.Objects;

/** Confirmation adapter whose EOF and cancellation behavior is always a decline. */
public final class BridgeConfirmationPrompt implements ConfirmationPrompt {
    private final BridgeSession session;

    public BridgeConfirmationPrompt(BridgeSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public boolean confirm(String prompt) {
        session.emit(new BridgeEvent.ConfirmationRequiredEvent(BridgeProtocol.VERSION, prompt));
        return session.readRequest().map(request -> switch (request) {
            case BridgeRequest.ConfirmRequest confirm -> confirm.accepted();
            case BridgeRequest.CancelRequest ignored -> false;
            case BridgeRequest.AnswerRequest ignored -> throw unexpected();
            case BridgeRequest.SkipRequest ignored -> throw unexpected();
            case ProvenanceConsentRequest ignored -> throw unexpected();
        }).orElse(false);
    }

    private static BridgeProtocolException unexpected() {
        return new BridgeProtocolException("Unexpected bridge request for confirmation.");
    }
}
