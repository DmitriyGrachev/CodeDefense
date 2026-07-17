package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportMetadataTest {
    @Test
    void preservesSelectedFileOrderWithoutRetainingSnapshotPrompt() {
        ProjectSnapshot snapshot = snapshot();
        ReportMetadata metadata = ReportMetadata.from(snapshot, " model ", Instant.parse("2026-07-17T10:15:30Z"));

        assertEquals(List.of("src/First.java", "src/Second.java"), metadata.selectedFiles().stream().map(AnalyzedFile::path).toList());
        assertEquals(snapshot.promptBytes(), metadata.snapshotBytes());
        assertEquals(2, metadata.redactionCount());
        assertThrows(UnsupportedOperationException.class, () -> metadata.selectedFiles().clear());
        assertFalse(metadata.toString().contains("SNAPSHOT_PROMPT_SECRET"));
    }

    @Test
    void requiresNonemptyUniqueFilesAndPositiveSnapshotBytes() {
        Instant now = Instant.parse("2026-07-17T10:15:30Z");
        assertThrows(IllegalArgumentException.class, () -> new ReportMetadata(now, "model", "project", "Java", List.of(), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReportMetadata(now, "model", "project", "Java",
                List.of(new AnalyzedFile("App.java", 1, false, 1), new AnalyzedFile("App.java", 2, false, 2)), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReportMetadata(now, "model", "project", "Java",
                List.of(new AnalyzedFile("App.java", 1, false, 1)), 0, 0));
    }

    private static ProjectSnapshot snapshot() {
        String prompt = "SNAPSHOT_PROMPT_SECRET";
        List<ProjectSnapshot.SelectedFile> files = new ArrayList<>(List.of(
                new ProjectSnapshot.SelectedFile(Path.of("src/First.java"), 3, false, 20),
                new ProjectSnapshot.SelectedFile(Path.of("src/Second.java"), 4, true, 30)));
        return new ProjectSnapshot(Path.of("project"), " project ", " Java ",
                new ScanSummary(Path.of("project"), 1, 0, List.of(new SourceFile(Path.of("src/First.java")))),
                files, prompt, prompt.getBytes(StandardCharsets.UTF_8).length, 2);
    }
}
