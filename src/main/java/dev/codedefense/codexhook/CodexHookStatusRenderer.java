package dev.codedefense.codexhook;

import dev.codedefense.domain.StagedPassportGateReason;
import dev.codedefense.domain.StagedPassportGateResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/** Renders the source-free advisory response consumed by a Codex Stop hook. */
public final class CodexHookStatusRenderer {
    public static final int MAXIMUM_OUTPUT_BYTES = 4 * 1024;

    public Optional<byte[]> render(StagedPassportGateResult result) {
        Objects.requireNonNull(result, "result");
        Optional<String> message = switch (result.state()) {
            case NO_STAGED_CHANGE -> Optional.empty();
            case UNDEFENDED -> Optional.of((
                    "CodeDefense: UNDEFENDED — %d staged files, +%d/-%d.\n"
                            + "Run a CodeDefense staged defense before committing.")
                    .formatted(result.stagedFileCount(), result.addedLines(), result.deletedLines()));
            case CURRENT -> Optional.of("CodeDefense: CURRENT — Passport %s, attempt %d."
                    .formatted(result.diffFingerprint().substring(0, 12), result.attemptNumber()));
            case EXPIRED -> Optional.of((
                    "CodeDefense: EXPIRED — %d staged files, +%d/-%d; "
                            + "the staged change no longer matches its Passport.\n"
                            + "Run a new defense for the current staged change.")
                    .formatted(result.stagedFileCount(), result.addedLines(), result.deletedLines()));
            case UNAVAILABLE -> result.reason() == StagedPassportGateReason.INVALID_REPOSITORY
                    ? Optional.empty()
                    : Optional.of("CodeDefense: UNAVAILABLE — staged Passport status could not be determined safely.");
        };
        return message.map(CodexHookStatusRenderer::encode);
    }

    private static byte[] encode(String message) {
        StringBuilder json = new StringBuilder("{\"continue\":true,\"systemMessage\":");
        appendQuoted(json, message);
        json.append("}\n");
        byte[] encoded = json.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_OUTPUT_BYTES) {
            throw new IllegalArgumentException("Codex hook output exceeds the maximum size");
        }
        return encoded;
    }

    private static void appendQuoted(StringBuilder target, String value) {
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\b' -> target.append("\\b");
                case '\f' -> target.append("\\f");
                case '\n' -> target.append("\\n");
                case '\r' -> target.append("\\r");
                case '\t' -> target.append("\\t");
                default -> {
                    if (character < 0x20) {
                        target.append("\\u%04x".formatted((int) character));
                    } else {
                        target.append(character);
                    }
                }
            }
        }
        target.append('"');
    }
}
