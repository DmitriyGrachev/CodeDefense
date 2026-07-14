package dev.codedefense.scanner;

public final class LineNumberFormatter {
    public String format(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.endsWith("\n")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return "";
        String[] lines = normalized.split("\n", -1); StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) out.append(i + 1).append(" | ").append(lines[i]).append('\n');
        return out.toString();
    }
}
