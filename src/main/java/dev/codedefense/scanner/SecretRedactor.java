package dev.codedefense.scanner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretRedactor {
    private static final String KEYS = "password|passwd|secret|apiKey|api_key|api-key|token|accessToken|access_token|clientSecret|client_secret|authorization";
    private static final Pattern JSON = Pattern.compile("(?i)(\"(?:" + KEYS + ")\"\\s*:\\s*\")(.*?)(\")");
    private static final Pattern ASSIGNMENT = Pattern.compile("(?im)(\\b(?:" + KEYS + ")\\b\\s*[:=]\\s*)(?!\")([^\\r\\n,}]+)");
    private static final Pattern BEARER = Pattern.compile("(?im)(Authorization\\s*:\\s*Bearer\\s+)([^\\s\\r\\n]+)");
    public RedactionResult redact(String content) {
        int[] count = {0};
        String json = replace(JSON, content, count);
        String bearer = replace(BEARER, json, count);
        return new RedactionResult(replace(ASSIGNMENT, bearer, count), count[0]);
    }
    private String replace(Pattern pattern, String input, int[] count) {
        Matcher matcher = pattern.matcher(input); StringBuffer out = new StringBuffer();
        while (matcher.find()) { count[0]++; String suffix = matcher.groupCount() >= 3 ? matcher.group(3) : ""; matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + "[REDACTED]" + suffix)); }
        matcher.appendTail(out); return out.toString();
    }
    public record RedactionResult(String content, int replacementCount) {}
}
