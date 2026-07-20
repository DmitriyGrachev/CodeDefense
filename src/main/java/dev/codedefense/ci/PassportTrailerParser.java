package dev.codedefense.ci;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PassportTrailerParser {
    private static final String KEY = "CodeDefense-Passport:";
    private static final Pattern VALID = Pattern.compile("CodeDefense-Passport: sha256:([0-9a-f]{64})");

    public PassportTrailer parse(String message) {
        Objects.requireNonNull(message, "message");
        String normalized = message.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
        if (normalized.isEmpty()) return PassportTrailer.missing();
        int boundary = normalized.lastIndexOf("\n\n");
        String finalParagraph = boundary < 0 ? normalized : normalized.substring(boundary + 2);
        List<String> candidates = new ArrayList<>();
        for (String line : finalParagraph.split("\n", -1)) {
            if (line.regionMatches(true, 0, KEY, 0, KEY.length())) candidates.add(line);
        }
        if (candidates.isEmpty()) return PassportTrailer.missing();
        if (candidates.size() != 1) return PassportTrailer.malformed();
        Matcher matcher = VALID.matcher(candidates.getFirst());
        return matcher.matches() ? PassportTrailer.valid(matcher.group(1)) : PassportTrailer.malformed();
    }
}
