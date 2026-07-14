package dev.codedefense.scanner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretRedactor {
    private static final String KEYS = "password|passwd|secret|apiKey|api_key|api-key|token|accessToken|access_token|clientSecret|client_secret|authorization";
    private static final Pattern JSON = Pattern.compile("(?i)(\"(?:" + KEYS + ")\"\\s*:\\s*\")(?!\\[REDACTED])((?:\\\\.|[^\"\\\\])*)(\")");
    private static final Pattern JSON_UNTERMINATED = Pattern.compile("(?i)(\"(?:" + KEYS + ")\"\\s*:\\s*\")((?:\\\\.|[^\"\\\\])*)$");
    private static final Pattern QUOTED = Pattern.compile("(?im)(\\b(?:" + KEYS + ")\\b\\s*[:=]\\s*)([\"'])((?:\\\\.|[^\"'\\\\])*)([\"'])");
    private static final Pattern UNTERMINATED_DOUBLE = Pattern.compile("(?im)(\\b(?:" + KEYS + ")\\b\\s*[:=]\\s*\")((?:\\\\.|[^\"\\\\])*)$");
    private static final Pattern UNTERMINATED_SINGLE = Pattern.compile("(?im)(\\b(?:" + KEYS + ")\\b\\s*[:=]\\s*')((?:\\\\.|[^'\\\\])*)$");
    private static final Pattern QUOTED_YAML_KEY = Pattern.compile("(?im)([\"'](?:" + KEYS + ")[\"']\\s*:\\s*)(?!\\s*[\"'])([^\\r\\n]+)");
    private static final Pattern QUOTED_YAML_DOUBLE = Pattern.compile("(?im)([\"'](?:" + KEYS + ")[\"']\\s*:\\s*\")(?!\\[REDACTED])((?:\\\\.|[^\"\\\\])*)(\")");
    private static final Pattern QUOTED_YAML_SINGLE = Pattern.compile("(?im)([\"'](?:" + KEYS + ")[\"']\\s*:\\s*')(?!\\[REDACTED])((?:\\\\.|[^'\\\\])*)(')");
    private static final Pattern QUOTED_YAML_UNTERMINATED_DOUBLE = Pattern.compile("(?im)([\"'](?:" + KEYS + ")[\"']\\s*:\\s*\")(?!\\[REDACTED])((?:\\\\.|[^\"\\\\])*)$");
    private static final Pattern QUOTED_YAML_UNTERMINATED_SINGLE = Pattern.compile("(?im)([\"'](?:" + KEYS + ")[\"']\\s*:\\s*')(?!\\[REDACTED])((?:\\\\.|[^'\\\\])*)$");
    private static final Pattern BEARER = Pattern.compile("(?im)(Authorization\\s*:\\s*Bearer\\s+)([^\\s\\r\\n]+)");
    private static final Pattern UNQUOTED = Pattern.compile("(?im)(\\b(?:" + KEYS + ")\\b\\s*[:=]\\s*)(?!(?i:Bearer\\s+\\[REDACTED]))([^\\s\"'][^\\r\\n]*)");

    public RedactionResult redact(String content) {
        Counter count = new Counter();
        String result = replaceJson(JSON, content, count);
        result = replaceUnterminatedJson(result, count);
        result = replaceQuoted(QUOTED, result, count);
        result = replaceUnterminatedAssignment(UNTERMINATED_DOUBLE, result, count);
        result = replaceUnterminatedAssignment(UNTERMINATED_SINGLE, result, count);
        result = replaceJson(QUOTED_YAML_DOUBLE, result, count);
        result = replaceJson(QUOTED_YAML_SINGLE, result, count);
        result = replaceUnterminatedAssignment(QUOTED_YAML_UNTERMINATED_DOUBLE, result, count);
        result = replaceUnterminatedAssignment(QUOTED_YAML_UNTERMINATED_SINGLE, result, count);
        result = replaceUnquoted(QUOTED_YAML_KEY, result, count);
        result = replaceBearer(result, count);
        result = replaceUnquoted(UNQUOTED, result, count);
        return new RedactionResult(result, count.value);
    }

    private String replaceJson(Pattern pattern, String input, Counter count) {
        Matcher matcher = pattern.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) { count.value++; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "[REDACTED]" + matcher.group(3))); }
        matcher.appendTail(out); return out.toString();
    }
    private String replaceUnterminatedJson(String input, Counter count) {
        Matcher matcher = JSON_UNTERMINATED.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) { count.value++; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "[REDACTED]")); }
        matcher.appendTail(out); return out.toString();
    }
    private String replaceQuoted(Pattern pattern, String input, Counter count) {
        Matcher matcher = pattern.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) { count.value++; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + "[REDACTED]" + matcher.group(4))); }
        matcher.appendTail(out); return out.toString();
    }
    private String replaceBearer(String input, Counter count) {
        Matcher matcher = BEARER.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) { count.value++; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "[REDACTED]")); }
        matcher.appendTail(out); return out.toString();
    }
    private String replaceUnterminatedAssignment(Pattern pattern, String input, Counter count) {
        Matcher matcher = pattern.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) { count.value++; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "[REDACTED]")); }
        matcher.appendTail(out); return out.toString();
    }
    private String replaceUnquoted(Pattern pattern, String input, Counter count) {
        Matcher matcher = pattern.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) { count.value++; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "[REDACTED]")); }
        matcher.appendTail(out); return out.toString();
    }
    private static final class Counter { private int value; }
    public record RedactionResult(String content, int replacementCount) { }
}
