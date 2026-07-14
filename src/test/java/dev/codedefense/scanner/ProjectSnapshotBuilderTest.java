package dev.codedefense.scanner;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSnapshotBuilderTest {
    @TempDir
    Path root;

    @Test
    void capsThirtyOneReadableCandidatesAtThirty() throws Exception {
        List<SourceFile> files = new ArrayList<>();
        for (int index = 0; index < 31; index++) {
            Path path = root.resolve("File" + index + ".java");
            Files.writeString(path, "class File" + index + " {}");
            files.add(new SourceFile(path.getFileName(), Files.size(path)));
        }

        var snapshot = new ProjectSnapshotBuilder(new CodeDefenseConfig(30, 120 * 1024, 24 * 1024))
                .build(new ScanSummary(root, 31, 0, files));

        assertEquals(30, snapshot.selectedFiles().size());
    }

    @Test
    void includesLongSingleLineAsTruncatedPrefixWithinBudgets() throws Exception {
        Path path = root.resolve("Long.java");
        Files.writeString(path, "x".repeat(5_000));
        var config = new CodeDefenseConfig(1, 2_000, 1_000);

        var snapshot = new ProjectSnapshotBuilder(config).build(summary(path));

        assertEquals(1, snapshot.selectedFiles().size());
        assertTrue(snapshot.selectedFiles().getFirst().truncated());
        assertTrue(snapshot.selectedFiles().getFirst().renderedBytes() <= config.maximumFileBlockBytes());
        assertTrue(snapshot.promptBytes() <= config.maximumSnapshotBytes());
    }

    @Test
    void redactsTruncatedQuotedSecretBeforeEnteringPrompt() throws Exception {
        Path path = root.resolve("Secrets.java");
        Files.writeString(path, "password=\"abc\\\"TOPSECRET-" + "x".repeat(5_000));
        var config = new CodeDefenseConfig(1, 2_000, 1_000);

        var snapshot = new ProjectSnapshotBuilder(config).build(summary(path));

        assertFalse(snapshot.promptContent().contains("TOPSECRET"));
        assertTrue(snapshot.promptContent().contains("[REDACTED]"));
        assertEquals(1, snapshot.redactionCount());
        assertTrue(snapshot.selectedFiles().getFirst().truncated());
    }

    private ScanSummary summary(Path path) throws Exception {
        return new ScanSummary(root, 1, 0, List.of(new SourceFile(path.getFileName(), Files.size(path))));
    }
}
