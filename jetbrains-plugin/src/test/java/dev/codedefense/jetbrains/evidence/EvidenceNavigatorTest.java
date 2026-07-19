package dev.codedefense.jetbrains.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.codedefense.jetbrains.process.EvidenceLocationView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceNavigatorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void opensSafeExistingFileAtRequestedZeroBasedLine() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        Path source = write(root.resolve("src/Main.java"), "one\ntwo\nthree\n");
        RecordingEditorOpener opener = new RecordingEditorOpener(4);
        EvidenceNavigator navigator = new IntelliJEvidenceNavigator(root, opener);

        EvidenceNavigator.NavigationResult result = navigator.open(
                new EvidenceLocationView("src/Main.java", 3, 4));

        assertTrue(result.opened());
        assertEquals(source.toRealPath(), opener.resolvedFiles.getFirst());
        assertEquals(List.of(2), opener.openedLines);
    }

    @Test
    void unsafeRelativeAndAbsolutePathsAreRejectedBeforeOpening() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        RecordingEditorOpener opener = new RecordingEditorOpener(1);
        new IntelliJEvidenceNavigator(root, opener);

        assertThrows(IllegalArgumentException.class,
                () -> new EvidenceLocationView("../outside.java", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new EvidenceLocationView(temporaryDirectory.resolve("outside.java").toString(), 1, 1));
        assertTrue(opener.resolvedFiles.isEmpty());
    }

    @Test
    void rejectsFinalSymbolicLink() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        Path target = write(root.resolve("Target.java"), "class Target {}\n");
        Path link = root.resolve("Link.java");
        createSymbolicLinkOrSkip(link, target);
        RecordingEditorOpener opener = new RecordingEditorOpener(1);

        EvidenceNavigator.NavigationResult result = new IntelliJEvidenceNavigator(root, opener)
                .open(new EvidenceLocationView("Link.java", 1, 1));

        assertFalse(result.opened());
        assertEquals(EvidenceNavigator.NavigationStatus.UNSAFE, result.status());
        assertTrue(opener.resolvedFiles.isEmpty());
    }

    @Test
    void rejectsIntermediateDirectorySymbolicLinkLeadingOutsideRoot() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        write(outside.resolve("Secret.java"), "class Secret {}\n");
        createSymbolicLinkOrSkip(root.resolve("linked"), outside);
        RecordingEditorOpener opener = new RecordingEditorOpener(1);

        EvidenceNavigator.NavigationResult result = new IntelliJEvidenceNavigator(root, opener)
                .open(new EvidenceLocationView("linked/Secret.java", 1, 1));

        assertFalse(result.opened());
        assertEquals(EvidenceNavigator.NavigationStatus.UNSAFE, result.status());
        assertTrue(opener.resolvedFiles.isEmpty());
    }

    @Test
    void rejectsMissingDeletedFileAndDirectory() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        Files.createDirectory(root.resolve("src"));
        RecordingEditorOpener opener = new RecordingEditorOpener(1);
        EvidenceNavigator navigator = new IntelliJEvidenceNavigator(root, opener);

        EvidenceNavigator.NavigationResult missing = navigator.open(
                new EvidenceLocationView("Deleted.java", 1, 1));
        EvidenceNavigator.NavigationResult directory = navigator.open(
                new EvidenceLocationView("src", 1, 1));

        assertEquals(EvidenceNavigator.NavigationStatus.UNAVAILABLE, missing.status());
        assertEquals(EvidenceNavigator.NavigationStatus.UNAVAILABLE, directory.status());
        assertTrue(opener.resolvedFiles.isEmpty());
    }

    @Test
    void rejectsUnreadableFileWherePermissionsAreEnforced() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        Path source = write(root.resolve("Main.java"), "class Main {}\n");
        assumeTrue(source.toFile().setReadable(false, false));
        try {
            assumeTrue(!Files.isReadable(source), "Platform still reports the file as readable");
            RecordingEditorOpener opener = new RecordingEditorOpener(1);

            EvidenceNavigator.NavigationResult result = new IntelliJEvidenceNavigator(root, opener)
                    .open(new EvidenceLocationView("Main.java", 1, 1));

            assertEquals(EvidenceNavigator.NavigationStatus.UNAVAILABLE, result.status());
            assertTrue(opener.resolvedFiles.isEmpty());
        } finally {
            source.toFile().setReadable(true, false);
        }
    }

    @Test
    void rejectsStaleLineBeyondCurrentEditorDocument() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        Path source = write(root.resolve("Main.java"), "one\ntwo\nthree\n");
        EvidenceLocationView receipt = new EvidenceLocationView("Main.java", 3, 3);
        Files.writeString(source, "one\ntwo", StandardCharsets.UTF_8);
        RecordingEditorOpener opener = new RecordingEditorOpener(2);
        EvidenceNavigator navigator = new IntelliJEvidenceNavigator(root, opener);

        EvidenceNavigator.NavigationResult result = navigator.open(receipt);

        assertEquals(EvidenceNavigator.NavigationStatus.STALE, result.status());
        assertTrue(opener.openedLines.isEmpty());
    }

    @Test
    void rejectsFileReplacedBySymbolicLinkAfterEvidenceWasReceived() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("project"));
        Path source = write(root.resolve("Main.java"), "class Main {}\n");
        Path outside = write(temporaryDirectory.resolve("Outside.java"), "class Outside {}\n");
        EvidenceLocationView receipt = new EvidenceLocationView("Main.java", 1, 1);
        RecordingEditorOpener opener = new RecordingEditorOpener(1);
        EvidenceNavigator navigator = new IntelliJEvidenceNavigator(root, opener);
        Files.delete(source);
        createSymbolicLinkOrSkip(source, outside);

        EvidenceNavigator.NavigationResult result = navigator.open(receipt);

        assertEquals(EvidenceNavigator.NavigationStatus.UNSAFE, result.status());
        assertTrue(opener.resolvedFiles.isEmpty());
    }

    private static Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Symbolic links are not supported: " + exception.getClass().getSimpleName());
        }
    }

    private static final class RecordingEditorOpener implements IntelliJEvidenceNavigator.EditorOpener {
        private final int lineCount;
        private final List<Path> resolvedFiles = new ArrayList<>();
        private final List<Integer> openedLines = new ArrayList<>();

        private RecordingEditorOpener(int lineCount) {
            this.lineCount = lineCount;
        }

        @Override
        public IntelliJEvidenceNavigator.EditorTarget resolve(Path safeFile) {
            resolvedFiles.add(safeFile);
            return new IntelliJEvidenceNavigator.EditorTarget() {
                @Override public int lineCount() { return lineCount; }
                @Override public void navigate(int zeroBasedLine) { openedLines.add(zeroBasedLine); }
            };
        }
    }
}
