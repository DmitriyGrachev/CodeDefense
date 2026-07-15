package dev.codedefense.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * An empty isolated workspace used solely for Codex schema and final-message files.
 */
public final class CodexTemporaryWorkspace implements AutoCloseable {
    private final Path workspace;
    private final Path schemaPath;
    private final Path finalMessagePath;
    private boolean closed;

    private CodexTemporaryWorkspace(Path workspace) {
        this.workspace = workspace;
        this.schemaPath = workspace.resolve("schema.json");
        this.finalMessagePath = workspace.resolve("final-message.json");
    }

    public static CodexTemporaryWorkspace create() throws IOException {
        return new CodexTemporaryWorkspace(Files.createTempDirectory("codedefense-codex-"));
    }

    public static CodexTemporaryWorkspace create(Path parent) throws IOException {
        Objects.requireNonNull(parent, "Temporary parent");
        return new CodexTemporaryWorkspace(Files.createTempDirectory(parent, "codedefense-codex-"));
    }

    public Path workspace() {
        return workspace;
    }

    public Path schemaPath() {
        return schemaPath;
    }

    public Path finalMessagePath() {
        return finalMessagePath;
    }

    public void writeSchema(String schemaJson) throws IOException {
        Files.writeString(schemaPath, Objects.requireNonNull(schemaJson, "Schema JSON"), StandardCharsets.UTF_8);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clean temporary Codex workspace.", exception);
        }
    }

    @FunctionalInterface
    public interface Factory {
        CodexTemporaryWorkspace create() throws IOException;
    }
}
