package dev.codedefense.report;

/** Escapes untrusted text before it is placed in Markdown controlled by the application. */
public final class MarkdownTextEscaper {
    private MarkdownTextEscaper() {
    }

    public static String inline(String value) {
        String normalized = normalize(value);
        StringBuilder escaped = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t' || codePoint == 0x2028 || codePoint == 0x2029) {
                escaped.append(' ');
            } else if (!Character.isISOControl(codePoint)) {
                appendInline(escaped, codePoint);
            }
        });
        return escaped.toString().strip();
    }

    public static String fencedText(String value) {
        String content = normalize(value);
        int fenceLength = longestBacktickRun(content) + 1;
        String fence = "`".repeat(Math.max(3, fenceLength));
        return fence + "text\n" + content + (content.endsWith("\n") ? "" : "\n") + fence;
    }

    private static void appendInline(StringBuilder escaped, int codePoint) {
        switch (codePoint) {
            case '&' -> escaped.append("&amp;");
            case '<' -> escaped.append("&lt;");
            case '>' -> escaped.append("&gt;");
            case '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '~', '=' -> {
                escaped.append('\\').appendCodePoint(codePoint);
            }
            default -> escaped.appendCodePoint(codePoint);
        }
    }

    private static int longestBacktickRun(String value) {
        int longest = 0;
        int current = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '`') {
                longest = Math.max(longest, ++current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String carriageReturnsNormalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder normalized = new StringBuilder(carriageReturnsNormalized.length());
        carriageReturnsNormalized.codePoints().forEach(codePoint -> {
            if (isLineControlSeparator(codePoint)) {
                normalized.append('\n');
            } else if (codePoint == '\n' || codePoint == '\t' || !Character.isISOControl(codePoint)) {
                normalized.appendCodePoint(codePoint);
            }
        });
        return normalized.toString();
    }

    private static boolean isLineControlSeparator(int codePoint) {
        return codePoint == 0x000B || codePoint == 0x000C || (codePoint >= 0x001C && codePoint <= 0x001E)
                || codePoint == 0x0085 || codePoint == 0x2028 || codePoint == 0x2029;
    }
}
