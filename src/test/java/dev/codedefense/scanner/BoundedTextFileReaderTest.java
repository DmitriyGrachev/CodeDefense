package dev.codedefense.scanner;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
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

    @Test
    void acceptsRegularFileWhenProjectRootIsReachedThroughDirectoryLink() throws Exception {
        Path realRoot = Files.createDirectory(root.resolve("real-project"));
        Files.writeString(realRoot.resolve("Example.java"), "class Example {}", StandardCharsets.UTF_8);
        Path linkedRoot = root.resolve("linked-project");
        try {
            Files.createSymbolicLink(linkedRoot, realRoot);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, () -> "Directory symbolic links are unavailable: " + exception.getMessage());
            return;
        }

        var result = reader.read(linkedRoot, linkedRoot.resolve("Example.java"), 100);

        assertTrue(result.available(), result.problem());
        assertEquals("class Example {}", result.content());
    }
}
