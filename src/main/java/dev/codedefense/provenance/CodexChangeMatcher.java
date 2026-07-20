package dev.codedefense.provenance;

import dev.codedefense.ai.AppServerFileChange;
import dev.codedefense.change.CapturedGitChange;
import dev.codedefense.change.StagedHunk;
import dev.codedefense.domain.CodexProvenanceStatus;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.scanner.SecretRedactor;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministically compares only path and normalized hunk evidence. */
public final class CodexChangeMatcher {
    private static final Pattern HEADER = Pattern.compile(
            "^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(?: .*)?$");
    private final SecretRedactor redactor;

    public CodexChangeMatcher() { this(new SecretRedactor()); }
    CodexChangeMatcher(SecretRedactor redactor) { this.redactor = Objects.requireNonNull(redactor); }

    public ProvenanceMatch match(CapturedGitChange gitChange, List<AppServerFileChange> codexChanges) {
        Objects.requireNonNull(gitChange, "gitChange");
        Objects.requireNonNull(codexChanges, "codexChanges");
        Map<String, NormalizedChangeEvidence> git = gitEvidence(gitChange);
        Map<String, NormalizedChangeEvidence> codex = codexEvidence(codexChanges);
        List<String> matched = git.entrySet().stream()
                .filter(entry -> entry.getValue().equals(codex.get(entry.getKey())))
                .map(Map.Entry::getKey).sorted().toList();
        CodexProvenanceStatus status = matched.isEmpty() ? CodexProvenanceStatus.NO_MATCH
                : matched.size() == git.size() ? CodexProvenanceStatus.EXACT_CHANGE_MATCH
                : CodexProvenanceStatus.PARTIAL_PATH_MATCH;
        return new ProvenanceMatch(status, git.size(), matched);
    }

    private Map<String, NormalizedChangeEvidence> gitEvidence(CapturedGitChange captured) {
        Map<String, List<StagedHunk>> grouped = new LinkedHashMap<>();
        captured.change().files().forEach(file -> grouped.put(portable(file.path()), new ArrayList<>()));
        for (StagedHunk hunk : captured.hunks()) {
            List<StagedHunk> values = grouped.get(portable(hunk.file().path()));
            if (values != null && !hunk.truncated()) values.add(hunk);
        }
        Map<String, NormalizedChangeEvidence> result = new LinkedHashMap<>();
        captured.change().files().forEach(file -> {
            String path = portable(file.path());
            List<String> hashes = grouped.get(path).stream()
                    .map(this::fingerprint).toList();
            if (!hashes.isEmpty()) result.put(path, new NormalizedChangeEvidence(
                    path, status(file.status()), hashes));
        });
        if (result.isEmpty()) throw new IllegalArgumentException("Git change has no complete hunk evidence");
        return result;
    }

    private Map<String, NormalizedChangeEvidence> codexEvidence(List<AppServerFileChange> changes) {
        Map<String, AppServerFileChange> finalByPath = new LinkedHashMap<>();
        changes.stream().sorted(Comparator.comparing(AppServerFileChange::itemId))
                .forEach(change -> finalByPath.put(safePath(change.path()), change));
        Map<String, NormalizedChangeEvidence> result = new LinkedHashMap<>();
        finalByPath.forEach((path, change) -> {
            List<ParsedHunk> hunks = parsePatch(path, change.patch());
            if (!hunks.isEmpty()) result.put(path, new NormalizedChangeEvidence(path,
                    status(change.kind()), hunks.stream().map(this::fingerprint).toList()));
        });
        return result;
    }

    private String fingerprint(StagedHunk hunk) {
        return fingerprint(new ParsedHunk(hunk.oldStartLine(), hunk.oldLineCount(),
                hunk.newStartLine(), hunk.newLineCount(), hunk.unifiedContent()));
    }

    private String fingerprint(ParsedHunk hunk) {
        String changed = normalize(hunk.content()).lines()
                .filter(line -> line.startsWith("+") || line.startsWith("-"))
                .filter(line -> !line.startsWith("+++") && !line.startsWith("---"))
                .map(line -> line.charAt(0) + redactor.redact(line.substring(1)).content())
                .reduce("", (left, right) -> left + right + "\n");
        return sha256("%d,%d;%d,%d\0%s".formatted(hunk.oldStart(), hunk.oldCount(),
                hunk.newStart(), hunk.newCount(), changed));
    }

    private static List<ParsedHunk> parsePatch(String expectedPath, String patch) {
        String normalized = normalize(Objects.requireNonNull(patch, "patch"));
        List<ParsedHunk> result = new ArrayList<>();
        ParsedBuilder current = null;
        for (String line : normalized.split("\n", -1)) {
            if (line.startsWith("--- ") || line.startsWith("+++ ")) {
                validateHeaderPath(expectedPath, line.substring(4));
                continue;
            }
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                if (current != null) result.add(current.finish());
                current = new ParsedBuilder(number(header.group(1)), count(header.group(2)),
                        number(header.group(3)), count(header.group(4)));
            } else if (current != null && (line.startsWith(" ") || line.startsWith("+") || line.startsWith("-"))) {
                current.append(line);
            } else if (current != null && !line.isEmpty() && !line.equals("\\ No newline at end of file")) {
                throw new IllegalArgumentException("Codex patch is malformed");
            }
        }
        if (current != null) result.add(current.finish());
        return List.copyOf(result);
    }

    private static void validateHeaderPath(String expected, String raw) {
        String path = raw.split("\\t", 2)[0];
        if (path.equals("/dev/null")) return;
        if (path.startsWith("a/") || path.startsWith("b/")) path = path.substring(2);
        if (!safePath(path).equals(expected)) throw new IllegalArgumentException("patch path mismatch");
    }

    private static String safePath(String value) {
        try {
            String normalized = Objects.requireNonNull(value).replace('\\', '/');
            if (normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")
                    || normalized.indexOf('\0') >= 0 || normalized.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("unsafe Codex change path");
            }
            Path path = Path.of(normalized).normalize();
            if (path.startsWith("..")) throw new IllegalArgumentException("unsafe Codex change path");
            return portable(path);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("unsafe Codex change path", exception);
        }
    }

    private static String status(StagedFileStatus value) { return value.name(); }
    private static String status(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "add", "added", "create", "created" -> "ADDED";
            case "delete", "deleted", "remove", "removed" -> "DELETED";
            case "rename", "renamed", "move", "moved" -> "RENAMED";
            case "modify", "modified", "update", "updated" -> "MODIFIED";
            default -> "UNKNOWN";
        };
    }
    private static String portable(Path path) { return path.toString().replace('\\', '/'); }
    private static String normalize(String value) { return value.replace("\r\n", "\n").replace('\r', '\n'); }
    private static int number(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Codex patch is malformed", exception);
        }
    }
    private static int count(String value) { return value == null ? 1 : number(value); }
    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private record ParsedHunk(int oldStart, int oldCount, int newStart, int newCount, String content) {}
    private static final class ParsedBuilder {
        final int oldStart, oldCount, newStart, newCount;
        final StringBuilder content = new StringBuilder(); int oldSeen, newSeen;
        ParsedBuilder(int oldStart, int oldCount, int newStart, int newCount) {
            this.oldStart = oldStart; this.oldCount = oldCount; this.newStart = newStart; this.newCount = newCount;
        }
        void append(String line) {
            if (line.startsWith(" ")) { oldSeen++; newSeen++; }
            else if (line.startsWith("-")) oldSeen++; else if (line.startsWith("+")) newSeen++;
            if (oldSeen > oldCount || newSeen > newCount) throw new IllegalArgumentException("Codex patch is malformed");
            content.append(line).append('\n');
        }
        ParsedHunk finish() {
            if (oldSeen != oldCount || newSeen != newCount || content.isEmpty())
                throw new IllegalArgumentException("Codex patch is malformed");
            content.setLength(content.length() - 1);
            return new ParsedHunk(oldStart, oldCount, newStart, newCount, content.toString());
        }
    }
}
