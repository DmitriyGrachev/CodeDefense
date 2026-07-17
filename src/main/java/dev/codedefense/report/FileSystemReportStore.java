package dev.codedefense.report;

import dev.codedefense.domain.FinalReport;
import dev.codedefense.domain.SavedReport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Filesystem adapter which stores reports only below the configured CodeDefense root. */
public class FileSystemReportStore implements ReportStore {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final Set<String> RESERVED_WINDOWS_NAMES = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private final CodeDefensePaths paths;
    private final ReportConfig config;
    private final MarkdownReportRenderer renderer;
    private final Clock clock;

    public FileSystemReportStore(CodeDefensePaths paths, ReportConfig config, MarkdownReportRenderer renderer, Clock clock) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.config = Objects.requireNonNull(config, "config");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SavedReport save(FinalReport report) {
        Objects.requireNonNull(report, "report");
        byte[] rendered = strictUtf8(renderer.render(report));
        if (rendered.length > config.maximumReportBytes()) {
            throw ReportPersistenceException.saveFailure();
        }
        Path reportTemp = null;
        Path pointerTemp = null;
        Path savedDestination = null;
        try {
            prepareDirectories();
            removeOrphanTemps();
            Path destination = chooseDestination(report.metadata().projectName(), Instant.now(clock));
            reportTemp = Files.createTempFile(paths.reportsDirectory(), ".report-", ".tmp");
            Files.write(reportTemp, rendered, StandardOpenOption.TRUNCATE_EXISTING);
            moveWithoutOverwrite(reportTemp, destination);
            reportTemp = null;
            savedDestination = destination;

            byte[] pointer = strictUtf8(destination.toAbsolutePath().normalize() + "\n");
            if (pointer.length > config.maximumLatestPointerBytes()) {
                throw new IOException("pointer exceeds configured limit");
            }
            if (Files.exists(paths.latestPointer(), LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(paths.latestPointer())) {
                throw new IOException("unsafe latest pointer");
            }
            pointerTemp = Files.createTempFile(paths.rootDirectory(), ".latest-", ".tmp");
            Files.write(pointerTemp, pointer, StandardOpenOption.TRUNCATE_EXISTING);
            moveReplacingPointer(pointerTemp, paths.latestPointer());
            pointerTemp = null;
            return new SavedReport(destination.toAbsolutePath().normalize(), report.narrativeSource());
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(reportTemp);
            deleteQuietly(pointerTemp);
            deleteQuietly(savedDestination);
            throw ReportPersistenceException.saveFailure();
        }
    }

    @Override
    public Optional<String> readLatest() {
        try {
            if (!Files.exists(paths.rootDirectory(), LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            requireDirectory(paths.rootDirectory());
            if (!Files.exists(paths.latestPointer(), LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            requireDirectory(paths.reportsDirectory());
            requireRegularFile(paths.latestPointer());
            String pointer = readStrictUtf8(paths.latestPointer(), config.maximumLatestPointerBytes());
            Path report = parsePointer(pointer);
            requireRegularFile(report);
            return Optional.of(readStrictUtf8(report, config.maximumReportBytes()));
        } catch (IOException | RuntimeException exception) {
            throw ReportPersistenceException.readFailure();
        }
    }

    /** Package-private seam for exercising both report and pointer publication moves. */
    void move(Path source, Path target, CopyOption... options) throws IOException {
        Files.move(source, target, options);
    }

    private void moveWithoutOverwrite(Path source, Path target) throws IOException {
        try {
            move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void moveReplacingPointer(Path source, Path target) throws IOException {
        try {
            move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void prepareDirectories() throws IOException {
        ensureDirectory(paths.rootDirectory());
        ensureDirectory(paths.reportsDirectory());
    }

    private static void ensureDirectory(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("unsafe report directory");
            }
            return;
        }
        Files.createDirectory(directory);
    }

    private Path chooseDestination(String projectName, Instant timestamp) throws IOException {
        String base = FILE_TIME.format(timestamp) + "-" + slug(projectName);
        for (int suffix = 1; suffix < Integer.MAX_VALUE; suffix++) {
            String name = base + (suffix == 1 ? "" : "-" + suffix) + ".md";
            Path candidate = paths.reportsDirectory().resolve(name).normalize();
            if (!candidate.startsWith(paths.reportsDirectory()) || Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            return candidate;
        }
        throw new IOException("no available report name");
    }

    private String slug(String projectName) {
        StringBuilder slug = new StringBuilder();
        boolean previousDash = false;
        for (int index = 0; index < projectName.length(); index++) {
            char character = Character.toLowerCase(projectName.charAt(index));
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9') {
                slug.append(character);
                previousDash = false;
            } else if (!previousDash && !slug.isEmpty()) {
                slug.append('-');
                previousDash = true;
            }
            if (slug.length() >= config.maximumProjectSlugLength()) {
                break;
            }
        }
        while (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '-') {
            slug.setLength(slug.length() - 1);
        }
        String result = slug.isEmpty() ? "project" : slug.toString();
        return RESERVED_WINDOWS_NAMES.contains(result) ? "project-" + result : result;
    }

    private Path parsePointer(String pointer) throws IOException {
        if (!pointer.endsWith("\n") || pointer.indexOf('\r') >= 0) {
            throw new IOException("malformed pointer");
        }
        String value = pointer.substring(0, pointer.length() - 1);
        if (value.isEmpty() || value.indexOf('\n') >= 0) {
            throw new IOException("malformed pointer");
        }
        Path report = Path.of(value);
        if (!report.isAbsolute() || !report.equals(report.normalize()) || !report.getFileName().toString().endsWith(".md")) {
            throw new IOException("unsafe pointer");
        }
        report = report.toAbsolutePath().normalize();
        if (!report.startsWith(paths.reportsDirectory())) {
            throw new IOException("report escapes storage");
        }
        return report;
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("unsafe report file");
        }
    }

    private static void requireDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("unsafe report directory");
        }
    }

    private static byte[] strictUtf8(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw ReportPersistenceException.saveFailure();
        }
    }

    private static String readStrictUtf8(Path path, int maximumBytes) throws IOException {
        byte[] bytes = readBounded(path, maximumBytes);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("invalid UTF-8", exception);
        }
    }

    private static byte[] readBounded(Path path, int maximumBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if (total > maximumBytes) {
                    throw new IOException("file exceeds configured limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void removeOrphanTemps() throws IOException {
        removeTemps(paths.rootDirectory(), ".latest-");
        removeTemps(paths.reportsDirectory(), ".report-");
    }

    private static void removeTemps(Path directory, String prefix) throws IOException {
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                if (entry.getFileName().toString().startsWith(prefix) && entry.getFileName().toString().endsWith(".tmp")
                        && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(entry);
                }
            }
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
