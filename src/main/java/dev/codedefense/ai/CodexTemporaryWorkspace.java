package dev.codedefense.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    private final WorkspaceDeleter deleter;
    private final SchemaWriter schemaWriter;
    private boolean closed;

    private CodexTemporaryWorkspace(Path workspace, WorkspaceDeleter deleter, SchemaWriter schemaWriter) {
        this.workspace = workspace;
        this.schemaPath = workspace.resolve("schema.json");
        this.finalMessagePath = workspace.resolve("final-message.json");
        this.deleter = deleter;
        this.schemaWriter = schemaWriter;
    }

    public static CodexTemporaryWorkspace create() throws IOException {
        return new CodexTemporaryWorkspace(
                Files.createTempDirectory("codedefense-codex-"),
                CodexTemporaryWorkspace::deleteWorkspace,
                CodexTemporaryWorkspace::writeSchemaFile);
    }

    public static CodexTemporaryWorkspace create(Path parent) throws IOException {
        Objects.requireNonNull(parent, "Temporary parent");
        return new CodexTemporaryWorkspace(
                Files.createTempDirectory(parent, "codedefense-codex-"),
                CodexTemporaryWorkspace::deleteWorkspace,
                CodexTemporaryWorkspace::writeSchemaFile);
    }

    static CodexTemporaryWorkspace create(Path parent, WorkspaceDeleter deleter) throws IOException {
        return create(parent, deleter, CodexTemporaryWorkspace::writeSchemaFile);
    }

    static CodexTemporaryWorkspace create(Path parent, WorkspaceDeleter deleter, SchemaWriter schemaWriter)
            throws IOException {
        Objects.requireNonNull(parent, "Temporary parent");
        return new CodexTemporaryWorkspace(
                Files.createTempDirectory(parent, "codedefense-codex-"),
                Objects.requireNonNull(deleter, "Workspace deleter"),
                Objects.requireNonNull(schemaWriter, "Schema writer"));
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
        schemaWriter.write(schemaPath, Objects.requireNonNull(schemaJson, "Schema JSON"));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            closed = true;
            return;
        }
        try {
            deleter.delete(workspace);
            closed = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clean temporary Codex workspace.", exception);
        }
    }

    @FunctionalInterface
    public interface Factory {
        CodexTemporaryWorkspace create() throws IOException;
    }

    static void deleteWorkspace(Path workspace) throws IOException {
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
    }

    private static void writeSchemaFile(Path path, String schemaJson) throws IOException {
        Files.writeString(path, schemaJson, StandardCharsets.UTF_8);
    }
}

@FunctionalInterface
interface WorkspaceDeleter {
    void delete(Path workspace) throws IOException;
}

@FunctionalInterface
interface SchemaWriter {
    void write(Path schemaPath, String schemaJson) throws IOException;
}
