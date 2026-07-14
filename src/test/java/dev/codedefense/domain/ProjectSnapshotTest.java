package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectSnapshotTest {
    @Test
    void rejectsIncorrectPromptByteCount() {
        var summary = new ScanSummary(Path.of("project"), 1, 0, List.of(new SourceFile(Path.of("App.java"))));
        assertThrows(IllegalArgumentException.class, () -> new ProjectSnapshot(
                Path.of("project"), "project", "Java", summary, List.of(), "😀", 1, 0));
    }
}
