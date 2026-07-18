package dev.codedefense.sample;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extracts the fixed, text-only built-in sample through bounded no-follow filesystem operations. */
public final class SampleProjectExtractor {
    static final String TEMPORARY_PREFIX = "codedefense-sample-";
    static final String PROJECT_ROOT_NAME = "codedefense-sample-news-service";

    private static final Set<String> REQUIRED_LOGICAL_PATHS = Set.of(
            "README.md",
            "pom.xml",
            "src/main/java/com/codedefense/sample/news/Article.java",
            "src/main/java/com/codedefense/sample/news/ArticleApplication.java",
            "src/main/java/com/codedefense/sample/news/ArticleController.java",
            "src/main/java/com/codedefense/sample/news/ArticleCreatedEvent.java",
            "src/main/java/com/codedefense/sample/news/ArticleRepository.java",
            "src/main/java/com/codedefense/sample/news/ArticleScheduler.java",
            "src/main/java/com/codedefense/sample/news/ArticleService.java",
            "src/main/java/com/codedefense/sample/news/FeedArticle.java",
            "src/main/java/com/codedefense/sample/news/FeedClient.java",
            "src/main/java/com/codedefense/sample/news/NotificationPublisher.java",
            "src/main/java/com/codedefense/sample/news/OpenAiSummaryService.java",
            "src/main/java/com/codedefense/sample/news/RetryingArticleProcessor.java",
            "src/main/resources/application.yml");

    private final SampleProjectConfig config;
    private final ArchiveSupplier archiveSupplier;
    private final TemporaryDirectoryFactory temporaryDirectoryFactory;
    private final DeletionStrategy deletionStrategy;

    public SampleProjectExtractor() {
        this(SampleProjectConfig.defaults(),
                resourcePath -> SampleProjectExtractor.class.getClassLoader().getResourceAsStream(resourcePath),
                Files::createTempDirectory,
                SampleProjectExtractor::deleteTree);
    }

    SampleProjectExtractor(SampleProjectConfig config, ArchiveSupplier archiveSupplier,
            TemporaryDirectoryFactory temporaryDirectoryFactory, DeletionStrategy deletionStrategy) {
        this.config = Objects.requireNonNull(config, "Sample project config");
        this.archiveSupplier = Objects.requireNonNull(archiveSupplier, "Archive supplier");
        this.temporaryDirectoryFactory = Objects.requireNonNull(temporaryDirectoryFactory, "Temporary directory factory");
        this.deletionStrategy = Objects.requireNonNull(deletionStrategy, "Deletion strategy");
    }

    public ExtractedSampleProject extract() {
        Path temporaryWorkspace = null;
        try {
            byte[] archive = readArchive();
            validateCentralDirectory(archive);
            temporaryWorkspace = temporaryDirectoryFactory.create(TEMPORARY_PREFIX).toAbsolutePath().normalize();
            requireDirectory(temporaryWorkspace);
            Path projectRoot = temporaryWorkspace.resolve(PROJECT_ROOT_NAME).normalize();
            if (!projectRoot.startsWith(temporaryWorkspace)) {
                throw new IOException("Invalid project root");
            }
            Files.createDirectory(projectRoot);
            requireDirectory(projectRoot);
            extractArchive(archive, projectRoot);
            return new ExtractedSampleProject(projectRoot, temporaryWorkspace, deletionStrategy);
        } catch (SampleProjectException exception) {
            deleteQuietly(temporaryWorkspace);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(temporaryWorkspace);
            throw SampleProjectException.preparationFailure(exception);
        }
    }

    private byte[] readArchive() throws IOException {
        try (InputStream input = archiveSupplier.open(config.resourcePath())) {
            if (input == null) {
                throw SampleProjectException.unavailable();
            }
            ByteArrayOutputStream archive = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total = checkedAdd(total, read, config.maximumArchiveBytes());
                archive.write(buffer, 0, read);
            }
            return archive.toByteArray();
        }
    }

    private void extractArchive(byte[] archive, Path projectRoot) throws IOException {
        Set<String> extractedPaths = new LinkedHashSet<>();
        int expandedBytes = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries = checkedAdd(entries, 1, config.maximumEntries());
                String logicalPath = validateEntryName(entry);
                if (!extractedPaths.add(logicalPath)) {
                    throw new IOException("Duplicate archive entry");
                }
                if (!REQUIRED_LOGICAL_PATHS.contains(logicalPath)) {
                    throw new IOException("Unexpected archive entry");
                }
                byte[] contents = readEntry(zip, entry);
                expandedBytes = checkedAdd(expandedBytes, contents.length, config.maximumExpandedBytes());
                requireNonEmptyText(contents);
                writeNewRegularFile(projectRoot, logicalPath, contents);
                zip.closeEntry();
            }
        }
        if (!extractedPaths.equals(REQUIRED_LOGICAL_PATHS)) {
            throw new IOException("Embedded archive has an unexpected file set");
        }
    }

    private String validateEntryName(ZipEntry entry) throws IOException {
        String name = entry.getName();
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || entry.isDirectory() || name.endsWith("/")
                || name.length() > config.maximumEntryPathCharacters() || name.indexOf('\\') >= 0
                || name.startsWith("/") || name.startsWith("\\") || isDriveQualified(name)) {
            throw new IOException("Invalid archive entry");
        }
        List<String> segments = List.of(name.split("/", -1));
        if (segments.stream().anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            throw new IOException("Unsafe archive entry");
        }
        return name;
    }

    private static boolean isDriveQualified(String path) {
        return path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
    }

    private byte[] readEntry(ZipInputStream zip, ZipEntry entry) throws IOException {
        if (entry.getSize() > config.maximumEntryBytes()) {
            throw new IOException("Archive entry exceeds configured bound");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            total = checkedAdd(total, read, config.maximumEntryBytes());
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static int checkedAdd(int current, int added, int maximum) throws IOException {
        if (added < 0 || current > maximum - added) {
            throw new IOException("Configured archive bound exceeded");
        }
        return current + added;
    }

    private static void requireNonEmptyText(byte[] contents) throws IOException {
        if (contents.length == 0) {
            throw new IOException("Required archive entry is empty");
        }
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contents))
                    .toString();
            if (decoded.chars().anyMatch(character -> character == 0
                    || (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t'))) {
                throw new IOException("Archive entry is not text");
            }
        } catch (CharacterCodingException exception) {
            throw new IOException("Archive entry is not UTF-8 text", exception);
        }
    }

    private static void writeNewRegularFile(Path projectRoot, String logicalPath, byte[] contents) throws IOException {
        Path relativePath = Path.of(logicalPath);
        Path destination = projectRoot.resolve(relativePath).normalize();
        if (!destination.startsWith(projectRoot)) {
            throw new IOException("Archive entry escapes project root");
        }
        createDirectoriesWithoutLinks(projectRoot, relativePath.getParent());
        Files.write(destination, contents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        BasicFileAttributes attributes = Files.readAttributes(destination, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new IOException("Archive entry is not a regular file");
        }
    }

    private static void createDirectoriesWithoutLinks(Path root, Path relativeParent) throws IOException {
        if (relativeParent == null) {
            return;
        }
        Path current = root;
        for (Path segment : relativeParent) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectory(current);
            } else {
                Files.createDirectory(current);
                requireDirectory(current);
            }
        }
    }

    private static void requireDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
            throw new IOException("Expected a real directory");
        }
    }

    private static void validateCentralDirectory(byte[] archive) throws IOException {
        int end = findEndOfCentralDirectory(archive);
        int entries = unsignedShort(archive, end + 10);
        if (entries == 0xffff) {
            throw new IOException("ZIP64 archives are not supported");
        }
        long offset = unsignedInt(archive, end + 16);
        if (offset > archive.length - 46) {
            throw new IOException("Invalid ZIP central directory");
        }
        int position = (int) offset;
        for (int index = 0; index < entries; index++) {
            if (position > archive.length - 46 || unsignedInt(archive, position) != 0x02014b50L) {
                throw new IOException("Invalid ZIP central directory entry");
            }
            int nameLength = unsignedShort(archive, position + 28);
            int extraLength = unsignedShort(archive, position + 30);
            int commentLength = unsignedShort(archive, position + 32);
            int length = 46 + nameLength + extraLength + commentLength;
            if (length < 46 || position > archive.length - length) {
                throw new IOException("Invalid ZIP central directory length");
            }
            int unixMode = (int) (unsignedInt(archive, position + 38) >>> 16);
            int fileType = unixMode & 0170000;
            if (fileType != 0 && fileType != 0100000) {
                throw new IOException("Non-regular ZIP entry");
            }
            position += length;
        }
    }

    private static int findEndOfCentralDirectory(byte[] archive) throws IOException {
        int minimum = Math.max(0, archive.length - 65_557);
        for (int index = archive.length - 22; index >= minimum; index--) {
            if (unsignedInt(archive, index) == 0x06054b50L
                    && index + 22 + unsignedShort(archive, index + 20) == archive.length) {
                return index;
            }
        }
        throw new IOException("ZIP end record is missing");
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xff)
                | (((long) bytes[offset + 1] & 0xff) << 8)
                | (((long) bytes[offset + 2] & 0xff) << 16)
                | (((long) bytes[offset + 3] & 0xff) << 24);
    }

    static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteQuietly(Path temporaryWorkspace) {
        if (temporaryWorkspace == null) {
            return;
        }
        try {
            deletionStrategy.delete(temporaryWorkspace);
        } catch (IOException ignored) {
            // The primary safe preparation failure is more useful than cleanup diagnostics here.
        }
    }

    @FunctionalInterface
    interface ArchiveSupplier {
        InputStream open(String resourcePath) throws IOException;
    }

    @FunctionalInterface
    interface TemporaryDirectoryFactory {
        Path create(String prefix) throws IOException;
    }

    @FunctionalInterface
    interface DeletionStrategy {
        void delete(Path root) throws IOException;
    }

    public static final class ExtractedSampleProject implements AutoCloseable {
        private final Path projectRoot;
        private final Path temporaryWorkspace;
        private final DeletionStrategy deletionStrategy;
        private boolean closed;

        private ExtractedSampleProject(Path projectRoot, Path temporaryWorkspace, DeletionStrategy deletionStrategy) {
            this.projectRoot = projectRoot;
            this.temporaryWorkspace = temporaryWorkspace;
            this.deletionStrategy = deletionStrategy;
        }

        public Path projectRoot() {
            return projectRoot;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            try {
                deletionStrategy.delete(temporaryWorkspace);
                closed = true;
            } catch (IOException exception) {
                throw SampleProjectException.cleanupFailure(exception);
            }
        }
    }
}
