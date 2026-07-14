package dev.codedefense.scanner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

public final class BoundedTextFileReader {
    public ReadResult read(Path root, Path candidate, int limit) {
        Objects.requireNonNull(root); Objects.requireNonNull(candidate);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path file = candidate.toAbsolutePath().normalize();
        if (!file.startsWith(normalizedRoot)) return ReadResult.unavailable("outside project root");
        if (limit < 0 || Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return ReadResult.unavailable("not a regular file");
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 4);
            boolean truncated = bytes.length > limit;
            int count = Math.min(bytes.length, limit);
            while (count > 0 && (bytes[count - 1] & 0xC0) == 0x80) count--;
            String content;
            try { content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, count)).toString(); }
            catch (Exception invalid) { content = new String(bytes, 0, count, StandardCharsets.ISO_8859_1); }
            return new ReadResult(content, count, truncated, null);
        } catch (IOException exception) { return ReadResult.unavailable("unreadable or missing file"); }
    }
    public record ReadResult(String content, int bytesRead, boolean truncated, String problem) {
        static ReadResult unavailable(String problem) { return new ReadResult("", 0, false, problem); }
        public boolean available() { return problem == null; }
    }
}
