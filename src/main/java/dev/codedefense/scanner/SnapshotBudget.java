package dev.codedefense.scanner;

import java.nio.charset.StandardCharsets;

public final class SnapshotBudget {
    public int utf8Bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    public String prefix(String value, int maxBytes) {
        StringBuilder out = new StringBuilder(); int used = 0;
        for (int offset = 0; offset < value.length();) { int cp = value.codePointAt(offset); int bytes = cp <= 0x7F ? 1 : cp <= 0x7FF ? 2 : cp <= 0xFFFF ? 3 : 4; if (used + bytes > maxBytes) break; out.appendCodePoint(cp); used += bytes; offset += Character.charCount(cp); }
        return out.toString();
    }
}
