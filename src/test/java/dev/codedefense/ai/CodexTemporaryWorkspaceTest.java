package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexTemporaryWorkspaceTest {
    @TempDir
    Path temporaryParent;

    @Test
    void writesSchemaAsUtf8AndContainsNoRepositoryContent() throws Exception {
        try (CodexTemporaryWorkspace workspace = CodexTemporaryWorkspace.create(temporaryParent)) {
            workspace.writeSchema("{\"title\":\"Привет\"}");

            assertTrue(Files.isDirectory(workspace.workspace()));
            assertEquals("{\"title\":\"Привет\"}", Files.readString(workspace.schemaPath(), StandardCharsets.UTF_8));
            assertFalse(Files.exists(workspace.finalMessagePath()));
            try (Stream<Path> files = Files.list(workspace.workspace())) {
                assertEquals(List.of("schema.json"), files.map(path -> path.getFileName().toString()).toList());
            }
        }
    }

    @Test
    void deletesNestedContentWithoutFollowingLinksAndIsIdempotent() throws Exception {
        CodexTemporaryWorkspace workspace = CodexTemporaryWorkspace.create(temporaryParent);
        Path nested = Files.createDirectories(workspace.workspace().resolve("nested").resolve("child"));
        Files.writeString(nested.resolve("data.txt"), "data");
        Path outside = Files.writeString(temporaryParent.resolve("outside.txt"), "outside");
        try {
            Files.createSymbolicLink(workspace.workspace().resolve("outside-link"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // Symbolic links require platform permission; deletion still exercises ordinary nested content.
        }

        workspace.close();
        workspace.close();

        assertFalse(Files.exists(workspace.workspace()));
        assertEquals("outside", Files.readString(outside));
    }
}
