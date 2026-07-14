package dev.codedefense.scanner;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotComponentsTest {
    @TempDir Path temp;

    @Test void validatesConfigAndPrioritizesDeterministically() {
        assertThrows(IllegalArgumentException.class, () -> new CodeDefenseConfig(0, 1, 1));
        var files = List.of(new SourceFile(Path.of("z", "Service.java")), new SourceFile(Path.of("README.md")), new SourceFile(Path.of("a", "Service.java")));
        assertEquals(Path.of("README.md"), new FilePrioritizer().prioritize(files).getFirst().relativePath());
        assertEquals(Path.of("a", "Service.java"), new FilePrioritizer().prioritize(files).get(1).relativePath());
    }

    @Test void redactsSecretsWithoutRedactingMethodNamesAndFormatsLines() {
        var result = new SecretRedactor().redact("token=abc\ntokenService()\r\n\r\npassword: yes\r\n");
        assertFalse(result.content().contains("abc")); assertTrue(result.content().contains("tokenService()")); assertEquals(2, result.replacementCount());
        assertEquals("1 | a\n2 | \n3 | b\n", new LineNumberFormatter().format("a\r\n\r\nb\r\n"));
    }

    @Test void buildsBoundedRedactedDeterministicSnapshot() throws Exception {
        Files.writeString(temp.resolve("App.java"), "password=very-secret\n" + "😀".repeat(100), StandardCharsets.UTF_8);
        var summary = new ScanSummary(temp, 1, 0, List.of(new SourceFile(Path.of("App.java"), Files.size(temp.resolve("App.java")))));
        var snapshot = new ProjectSnapshotBuilder(new CodeDefenseConfig(30, 400, 200)).build(summary);
        assertTrue(snapshot.promptBytes() <= 400); assertEquals(snapshot.promptBytes(), snapshot.promptContent().getBytes(StandardCharsets.UTF_8).length);
        assertFalse(snapshot.promptContent().contains(temp.toString())); assertFalse(snapshot.promptContent().contains("very-secret"));
        assertEquals(snapshot.promptContent(), new ProjectSnapshotBuilder(new CodeDefenseConfig(30, 400, 200)).build(summary).promptContent());
    }
}
