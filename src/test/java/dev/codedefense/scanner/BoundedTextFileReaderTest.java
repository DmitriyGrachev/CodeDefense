package dev.codedefense.scanner;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedTextFileReaderTest {
    @TempDir Path root;
    private final BoundedTextFileReader reader = new BoundedTextFileReader();
    @Test void keepsCompleteCyrillicAndEmojiAtLimit() throws Exception {
        Path file=root.resolve("text.txt"); Files.writeString(file, "Ж😀", StandardCharsets.UTF_8);
        var all=reader.read(root,file,6); assertEquals("Ж😀",all.content()); assertFalse(all.truncated());
        var cut=reader.read(root,file,3); assertEquals("Ж",cut.content()); assertTrue(cut.truncated()); assertFalse(cut.content().contains("\uFFFD"));
    }
    @Test void rejectsOutsideRoot() throws Exception { Path outside=Files.createTempFile("outside", ".txt"); assertFalse(reader.read(root,outside,10).available()); }
    @Test void usesDeterministicFallbackForInvalidUtf8() throws Exception { Path file=root.resolve("bad.txt"); Files.write(file,new byte[]{'a',(byte)0xFF}); assertEquals("aÿ",reader.read(root,file,2).content()); }
}
