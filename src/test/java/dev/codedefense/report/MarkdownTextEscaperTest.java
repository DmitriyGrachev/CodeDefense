package dev.codedefense.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownTextEscaperTest {
    @Test
    void inline_contains_markdown_and_html_injection_while_preserving_unicode() {
        String escaped = MarkdownTextEscaper.inline("# title\r\n<script>& кириллица é\t[item]");

        assertEquals("\\# title &lt;script&gt;&amp; кириллица é \\[item\\]", escaped);
        assertFalse(escaped.contains("\r"));
        assertFalse(escaped.contains("\n"));
    }

    @Test
    void fenced_text_uses_a_longer_fence_than_any_content_backtick_run() {
        String fenced = MarkdownTextEscaper.fencedText("answer\r\n```\né");

        assertEquals("````text\nanswer\n```\né\n````", fenced);
        assertFalse(fenced.contains("\r"));
    }

    @Test
    void fenced_text_normalizes_every_line_separator_to_lf_without_changing_ordinary_unicode() {
        String fenced = MarkdownTextEscaper.fencedText("one\rtwo\u2028three\u2029four\u0085five\u000Bsix\u000Cemoji 🚀");

        assertEquals("```text\none\ntwo\nthree\nfour\nfive\nsix\nemoji 🚀\n```", fenced);
        assertOnlyLfLineBreaks(fenced);
    }

    @Test
    void inline_escapes_heading_link_table_and_backslash_payloads() {
        assertEquals("\\# heading \\[link\\]\\(target\\) \\| table \\| \\\\",
                MarkdownTextEscaper.inline("# heading [link](target) | table | \\"));
    }

    private static void assertOnlyLfLineBreaks(String text) {
        assertTrue(text.codePoints().allMatch(MarkdownTextEscaperTest::isAllowedCodePoint),
                () -> "Expected only LF line breaks: " + text);
    }

    private static boolean isAllowedCodePoint(int codePoint) {
        return codePoint == '\n' || !isLineBreak(codePoint);
    }

    private static boolean isLineBreak(int codePoint) {
        return codePoint == '\r' || codePoint == 0x000B || codePoint == 0x000C
                || (codePoint >= 0x001C && codePoint <= 0x001E) || codePoint == 0x0085
                || Character.getType(codePoint) == Character.LINE_SEPARATOR
                || Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR;
    }
}
