package dev.codedefense.scanner;

import dev.codedefense.domain.ScanSummary;
import dev.codedefense.domain.SourceFile;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FileSystemProjectScanner implements ProjectScanner {
    private final ProjectFileFilter filter;

    public FileSystemProjectScanner() {
        this(new ProjectFileFilter());
    }

    FileSystemProjectScanner(ProjectFileFilter filter) {
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public ScanSummary scan(Path root, ScanPolicy policy) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(policy, "policy");
        Path normalizedRoot = validateRoot(root);
        ScanCollector collector = new ScanCollector(normalizedRoot, filter);

        try {
            Files.walkFileTree(normalizedRoot, collector);
        } catch (IOException exception) {
            throw new InvalidProjectPathException("Project path is not readable: " + normalizedRoot, exception);
        }

        List<SourceFile> candidates = collector.candidates();
        if (candidates.isEmpty()) {
            throw new NoSupportedSourceFilesException("No supported source files found: " + normalizedRoot);
        }
        return new ScanSummary(normalizedRoot, collector.discoveredFileCount, collector.ignoredFileCount, candidates);
    }

    private Path validateRoot(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.exists(normalizedRoot)) {
            throw new InvalidProjectPathException("Project path does not exist: " + normalizedRoot);
        }
        if (!Files.isDirectory(normalizedRoot)) {
            throw new InvalidProjectPathException("Project path is not a directory: " + normalizedRoot);
        }
        if (!Files.isReadable(normalizedRoot)) {
            throw new InvalidProjectPathException("Project path is not readable: " + normalizedRoot);
        }
        return normalizedRoot;
    }

    private static final class ScanCollector extends SimpleFileVisitor<Path> {
        private final Path root;
        private final ProjectFileFilter filter;
        private final List<SourceFile> candidates = new ArrayList<>();
        private int discoveredFileCount;
        private int ignoredFileCount;

        private ScanCollector(Path root, ProjectFileFilter filter) {
            this.root = root;
            this.filter = filter;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (!directory.equals(root) && (attributes.isSymbolicLink() || filter.isExcludedDirectory(directory))) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            discoveredFileCount++;
            if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                ignoredFileCount++;
                return FileVisitResult.CONTINUE;
            }
            if (filter.isExcludedFile(file) || !filter.isSupportedFile(file)) {
                ignoredFileCount++;
                return FileVisitResult.CONTINUE;
            }

            candidates.add(new SourceFile(root.relativize(file), attributes.size()));
            return FileVisitResult.CONTINUE;
        }

        private List<SourceFile> candidates() {
            return candidates.stream()
                    .sorted(Comparator.comparing(source -> source.relativePath().toString()))
                    .toList();
        }
    }
}
