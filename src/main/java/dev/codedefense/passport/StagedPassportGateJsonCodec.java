package dev.codedefense.passport;

import dev.codedefense.domain.StagedPassportGateResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Strict, source-free JSON boundary for staged Change Passport gate results. */
public final class StagedPassportGateJsonCodec {
    public static final int MAXIMUM_OUTPUT_BYTES = 256 * 1024;

    public byte[] encode(StagedPassportGateResult result) {
        Objects.requireNonNull(result, "result");
        StringBuilder json = new StringBuilder();
        json.append('{');
        number(json, "protocolVersion", result.protocolVersion());
        text(json, "state", result.state().name());
        text(json, "reason", result.reason().name());
        text(json, "diffFingerprint", result.diffFingerprint());
        number(json, "attemptNumber", result.attemptNumber());
        number(json, "stagedFileCount", result.stagedFileCount());
        number(json, "addedLines", result.addedLines());
        number(json, "deletedLines", result.deletedLines());
        json.append("\"relativePaths\":[");
        for (int index = 0; index < result.relativePaths().size(); index++) {
            if (index > 0) json.append(',');
            quoted(json, result.relativePaths().get(index));
        }
        json.append("]}\n");
        byte[] encoded = json.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAXIMUM_OUTPUT_BYTES) {
            throw new IllegalArgumentException("Staged gate output exceeds the maximum size");
        }
        return encoded;
    }

    private static void number(StringBuilder target, String name, int value) {
        target.append('"').append(name).append("\":").append(value).append(',');
    }

    private static void text(StringBuilder target, String name, String value) {
        target.append('"').append(name).append("\":");
        quoted(target, value);
        target.append(',');
    }

    private static void quoted(StringBuilder target, String value) {
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
