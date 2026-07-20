package dev.codedefense.scanner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

public final class BoundedTextFileReader {
    public ReadResult read(Path root, Path candidate, int limit) {
        Objects.requireNonNull(root); Objects.requireNonNull(candidate);
        Path logicalRoot = root.toAbsolutePath().normalize();
        Path file = candidate.toAbsolutePath().normalize();
        if (!file.startsWith(logicalRoot)) return ReadResult.unavailable("outside project root");
        Path realRoot;
        try { realRoot = root.toRealPath(); }
        catch (IOException exception) { return ReadResult.unavailable("unreadable project root"); }
        Path realParent;
        try {
            if (file.getParent() == null) return ReadResult.unavailable("outside project root");
            realParent = file.getParent().toRealPath();
            if (!realParent.startsWith(realRoot)) return ReadResult.unavailable("outside project root");
            file = realParent.resolve(file.getFileName());
        } catch (IOException exception) { return ReadResult.unavailable("unreadable parent path"); }
        if (limit < 0 || Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return ReadResult.unavailable("not a regular file");
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            boolean truncated = bytes.length > limit;
            int count = Math.min(bytes.length, limit);
            String content;
            try { content = decode(bytes, count); }
            catch (Exception invalid) {
                int trimmed = count;
                if (truncated) for (int trim = 1; trim <= 3 && trimmed > 0; trim++) try { content = decode(bytes, count - trim); trimmed = count - trim; return new ReadResult(content, trimmed, true, null); } catch (Exception ignored) { }
                content = new String(bytes, 0, count, StandardCharsets.ISO_8859_1);
            }
            return new ReadResult(content, count, truncated, null);
        } catch (IOException exception) { return ReadResult.unavailable("unreadable or missing file"); }
    }
    private String decode(byte[] bytes, int count) throws java.nio.charset.CharacterCodingException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, count)).toString(); }
    public record ReadResult(String content, int bytesRead, boolean truncated, String problem) {
        static ReadResult unavailable(String problem) { return new ReadResult("", 0, false, problem); }
        public boolean available() { return problem == null; }
    }
}
