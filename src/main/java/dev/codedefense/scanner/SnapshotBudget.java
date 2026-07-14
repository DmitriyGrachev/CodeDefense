package dev.codedefense.scanner;

import java.nio.charset.StandardCharsets;

public final class SnapshotBudget {
    public int utf8Bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    public String prefix(String value, int maxBytes) {
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < value.length();) { int cp = value.codePointAt(offset); String next = new String(Character.toChars(cp)); if (utf8Bytes(out + next) > maxBytes) break; out.append(next); offset += Character.charCount(cp); }
        return out.toString();
    }
}
