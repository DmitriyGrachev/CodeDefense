package dev.codedefense.scanner;

import dev.codedefense.domain.ScanSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileSystemProjectScannerTest {
    @TempDir
    Path temporaryDirectory;

    private final ProjectScanner scanner = new FileSystemProjectScanner();

    @Test
    void rejectsMissingProjectDirectory() {
        assertThrows(InvalidProjectPathException.class, () -> scan(temporaryDirectory.resolve("missing")));
    }

    @Test
    void rejectsRegularFileAsProjectDirectory() throws IOException {
        Path file = Files.createFile(temporaryDirectory.resolve("project.txt"));

        assertThrows(InvalidProjectPathException.class, () -> scan(file));
    }

    @Test
    void prunesTargetDirectory() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("target"));
        write("target/Generated.java");
        write("App.java");

        ScanSummary summary = scan(temporaryDirectory);

        assertEquals(1, summary.acceptedCandidateCount());
        assertEquals(Path.of("App.java"), summary.candidates().getFirst().relativePath());
    }

    @Test
    void prunesNodeModulesDirectory() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("node_modules/library"));
        write("node_modules/library/index.js");
        write("app.ts");

        ScanSummary summary = scan(temporaryDirectory);

        assertEquals(1, summary.acceptedCandidateCount());
        assertEquals(Path.of("app.ts"), summary.candidates().getFirst().relativePath());
    }

    @Test
    void acceptsJavaSource() throws IOException {
        write("App.java");

        assertEquals(1, scan(temporaryDirectory).acceptedCandidateCount());
    }

    @Test
    void acceptsTypeScriptSource() throws IOException {
        write("app.ts");

        assertEquals(1, scan(temporaryDirectory).acceptedCandidateCount());
    }

    @Test
    void acceptsPomXml() throws IOException {
        write("pom.xml");

        assertEquals(1, scan(temporaryDirectory).acceptedCandidateCount());
    }

    @Test
    void rejectsEnvironmentFile() throws IOException {
        write(".env");
        write("App.java");

        ScanSummary summary = scan(temporaryDirectory);

        assertEquals(1, summary.acceptedCandidateCount());
        assertEquals(1, summary.ignoredFileCount());
    }

    @Test
    void rejectsPemAndKeyFiles() throws IOException {
        write("certificate.pem");
        write("private.key");
        write("App.java");

        ScanSummary summary = scan(temporaryDirectory);

        assertEquals(1, summary.acceptedCandidateCount());
        assertEquals(2, summary.ignoredFileCount());
    }

    @Test
    void doesNotFollowSymbolicLinks() throws IOException {
        Path linkedDirectory = temporaryDirectory.resolve("target");
        Files.createDirectories(linkedDirectory);
        write(linkedDirectory, "Linked.java");
        Path link = temporaryDirectory.resolve("linked");
        boolean created = createSymbolicLink(link, linkedDirectory);
        assumeTrue(created, "Symbolic links are unavailable on this platform");
        write("App.java");

        ScanSummary summary = scan(temporaryDirectory);

        assertEquals(1, summary.acceptedCandidateCount());
        assertFalse(summary.candidates().stream().anyMatch(file -> file.relativePath().startsWith("linked")));
    }

    @Test
    void rejectsAnEmptyProject() {
        NoSupportedSourceFilesException exception = assertThrows(
                NoSupportedSourceFilesException.class,
                () -> scan(temporaryDirectory)
        );

        assertTrue(exception.getMessage().startsWith("No supported source files found:"));
    }

    private ScanSummary scan(Path root) {
        return scanner.scan(root, ScanPolicy.defaults());
    }

    private void write(String relativePath) throws IOException {
        write(temporaryDirectory, relativePath);
    }

    private void write(Path root, String relativePath) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "test");
    }

    private boolean createSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            return false;
        }
    }
}
