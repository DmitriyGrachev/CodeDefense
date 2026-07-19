package dev.codedefense.bridge;

import dev.codedefense.terminal.UserInput;
import java.util.Objects;

/** Maps typed IDE messages onto the existing interview input port. */
public final class BridgeInterviewInput implements UserInput {
    private final BridgeSession session;

    public BridgeInterviewInput(BridgeSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public String readAnswer(String prompt) {
        return session.readRequest().map(request -> switch (request) {
            case BridgeRequest.AnswerRequest answer -> answer.answer();
            case BridgeRequest.SkipRequest ignored -> "skip";
            case BridgeRequest.CancelRequest ignored -> null;
            case BridgeRequest.ConfirmRequest ignored -> throw new BridgeProtocolException(
                    "Unexpected bridge request during interview.");
        }).orElse(null);
    }
}
