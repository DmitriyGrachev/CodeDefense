package dev.codedefense.jetbrains.evidence;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.codedefense.jetbrains.process.EvidenceLocationView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Filesystem boundary used before navigating to model-provided evidence locations. */
public final class IntelliJEvidenceNavigator implements EvidenceNavigator {
    private static final NavigationResult OPENED = result(NavigationStatus.OPENED, "Evidence opened.");
    private static final NavigationResult UNSAFE = result(NavigationStatus.UNSAFE, "Evidence path is unsafe.");
    private static final NavigationResult UNAVAILABLE = result(
            NavigationStatus.UNAVAILABLE, "Evidence file is unavailable.");
    private static final NavigationResult STALE = result(
            NavigationStatus.STALE, "Evidence location is stale.");

    private final Path realProjectRoot;
    private final EditorOpener editorOpener;

    public IntelliJEvidenceNavigator(Project project, Path projectRoot) {
        this(projectRoot, productionOpener(project));
    }

    IntelliJEvidenceNavigator(Path projectRoot, EditorOpener editorOpener) {
        this.realProjectRoot = requireRealProjectRoot(projectRoot);
        this.editorOpener = Objects.requireNonNull(editorOpener, "editorOpener");
    }

    @Override
    public NavigationResult open(EvidenceLocationView location) {
        Objects.requireNonNull(location, "location");
        SafeResolution resolution = resolve(location.relativePath());
        if (resolution.status() != null) return resolution.status();

        EditorTarget target;
        try {
            target = editorOpener.resolve(resolution.path());
        } catch (RuntimeException exception) {
            return UNAVAILABLE;
        }
        if (target == null) return UNAVAILABLE;
        int lineCount;
        try {
            lineCount = target.lineCount();
        } catch (RuntimeException exception) {
            return UNAVAILABLE;
        }
        if (lineCount < location.startLine()) return STALE;
        try {
            target.navigate(location.startLine() - 1);
            return OPENED;
        } catch (RuntimeException exception) {
            return UNAVAILABLE;
        }
    }

    private SafeResolution resolve(String portableRelativePath) {
        try {
            Path relative = Path.of(portableRelativePath);
            if (relative.isAbsolute() || relative.getNameCount() == 0 || relative.startsWith("..")) {
                return unsafe();
            }

            Path candidate = realProjectRoot.resolve(relative).normalize();
            if (!candidate.startsWith(realProjectRoot)) return unsafe();

            Path current = realProjectRoot;
            Path parent = relative.getParent();
            if (parent != null) {
                for (Path segment : parent) {
                    current = current.resolve(segment);
                    if (Files.isSymbolicLink(current)) return unsafe();
                    if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) return unavailable();
                    Path realParent = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
                    if (!realParent.startsWith(realProjectRoot)) return unsafe();
                    current = realParent;
                }
            }

            Path file = current.resolve(relative.getFileName()).normalize();
            if (!file.startsWith(realProjectRoot)) return unsafe();
            if (Files.isSymbolicLink(file)) return unsafe();
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file)) {
                return unavailable();
            }
            Path realFile = file.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realFile.startsWith(realProjectRoot) || !Objects.equals(realFile.getParent(), current)) {
                return unsafe();
            }
            return new SafeResolution(realFile, null);
        } catch (InvalidPathException | IOException | SecurityException exception) {
            return unavailable();
        }
    }

    private static Path requireRealProjectRoot(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        try {
            Path normalized = projectRoot.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized)
                    || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(normalized)) {
                throw invalidRoot();
            }
            return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException exception) {
            throw invalidRoot();
        }
    }

    private static EditorOpener productionOpener(Project project) {
        Objects.requireNonNull(project, "project");
        return safeFile -> {
            if (Files.isSymbolicLink(safeFile)
                    || !Files.isRegularFile(safeFile, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(safeFile);
            if (virtualFile == null || virtualFile.isDirectory() || !virtualFile.isValid()) return null;
            Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
            if (document == null) return null;
            return new EditorTarget() {
                @Override
                public int lineCount() {
                    return document.getLineCount();
                }

                @Override
                public void navigate(int zeroBasedLine) {
                    new OpenFileDescriptor(project, virtualFile, zeroBasedLine, 0).navigate(true);
                }
            };
        };
    }

    private static SafeResolution unsafe() {
        return new SafeResolution(null, UNSAFE);
    }

    private static SafeResolution unavailable() {
        return new SafeResolution(null, UNAVAILABLE);
    }

    private static NavigationResult result(NavigationStatus status, String message) {
        return new NavigationResult(status, message);
    }

    private static IllegalArgumentException invalidRoot() {
        return new IllegalArgumentException("Project root is invalid.");
    }

    interface EditorOpener {
        EditorTarget resolve(Path safeFile);
    }

    interface EditorTarget {
        int lineCount();
        void navigate(int zeroBasedLine);
    }

    private record SafeResolution(Path path, NavigationResult status) { }
}
