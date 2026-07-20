package dev.codedefense.jetbrains.commit;

import static dev.codedefense.jetbrains.commit.PassportTrailerResult.Status.ADDED;
import static dev.codedefense.jetbrains.commit.PassportTrailerResult.Status.ALREADY_PRESENT;
import static dev.codedefense.jetbrains.commit.PassportTrailerResult.Status.CONFLICT;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class PassportCommitTrailer {
    private static final String KEY = "CodeDefense-Passport:";
    private static final String VALUE_PREFIX = " sha256:";
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern GIT_TRAILER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9-]*:[ \\t]+\\S.*");

    public PassportTrailerResult apply(String commitMessage, String diffFingerprint) {
        Objects.requireNonNull(commitMessage, "commitMessage");
        Objects.requireNonNull(diffFingerprint, "diffFingerprint");
        if (!FINGERPRINT.matcher(diffFingerprint).matches()) {
            throw new IllegalArgumentException("diffFingerprint must be 64 lowercase hexadecimal characters");
        }

        String expected = KEY + VALUE_PREFIX + diffFingerprint;
        ParsedMessage parsed = parse(commitMessage);
        List<String> passportLines = parsed.lines().stream()
                .filter(PassportCommitTrailer::startsWithPassportKey)
                .toList();
        if (passportLines.size() > 1) {
            return new PassportTrailerResult(CONFLICT, commitMessage);
        }
        if (passportLines.size() == 1) {
            return new PassportTrailerResult(
                    passportLines.getFirst().equals(expected) ? ALREADY_PRESENT : CONFLICT,
                    commitMessage);
        }

        String lineEnding = parsed.firstLineEnding();
        String separator = separator(parsed, lineEnding);
        return new PassportTrailerResult(ADDED, commitMessage + separator + expected);
    }

    private static boolean startsWithPassportKey(String line) {
        return line.length() >= KEY.length()
                && line.regionMatches(true, 0, KEY, 0, KEY.length());
    }

    private static String separator(ParsedMessage parsed, String lineEnding) {
        if (parsed.lines().size() == 1 && parsed.lines().getFirst().isEmpty()) {
            return "";
        }
        if (hasExistingTrailerBlock(parsed.lines())) {
            return parsed.trailingLineEndings() == 0 ? lineEnding : "";
        }
        return switch (Math.min(parsed.trailingLineEndings(), 2)) {
            case 0 -> lineEnding + lineEnding;
            case 1 -> lineEnding;
            default -> "";
        };
    }

    private static boolean hasExistingTrailerBlock(List<String> lines) {
        int last = lines.size() - 1;
        while (last >= 0 && lines.get(last).isEmpty()) {
            last--;
        }
        if (last < 0 || !GIT_TRAILER.matcher(lines.get(last)).matches()) {
            return false;
        }
        int first = last;
        while (first > 0 && GIT_TRAILER.matcher(lines.get(first - 1)).matches()) {
            first--;
        }
        return first > 0 && lines.get(first - 1).isEmpty();
    }

    private static ParsedMessage parse(String message) {
        List<String> lines = new ArrayList<>();
        String firstLineEnding = null;
        int trailingLineEndings = 0;
        int lineStart = 0;
        int index = 0;
        while (index < message.length()) {
            int endingLength = lineEndingLength(message, index);
            if (endingLength == 0) {
                trailingLineEndings = 0;
                index++;
                continue;
            }
            lines.add(message.substring(lineStart, index));
            String ending = message.substring(index, index + endingLength);
            if (firstLineEnding == null) {
                firstLineEnding = ending;
            }
            trailingLineEndings++;
            index += endingLength;
            lineStart = index;
        }
        lines.add(message.substring(lineStart));
        if (lineStart < message.length()) {
            trailingLineEndings = 0;
        }
        return new ParsedMessage(List.copyOf(lines),
                firstLineEnding == null ? "\n" : firstLineEnding,
                trailingLineEndings);
    }

    private static int lineEndingLength(String message, int index) {
        char character = message.charAt(index);
        if (character == '\r') {
            return index + 1 < message.length() && message.charAt(index + 1) == '\n' ? 2 : 1;
        }
        return character == '\n' ? 1 : 0;
    }

    private record ParsedMessage(List<String> lines, String firstLineEnding, int trailingLineEndings) { }
}
