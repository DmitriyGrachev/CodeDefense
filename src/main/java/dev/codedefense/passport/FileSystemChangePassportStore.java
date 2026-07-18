package dev.codedefense.passport;

import dev.codedefense.domain.ChangePassport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** No-follow-link filesystem store for source-free Markdown Change Passports. */
public final class FileSystemChangePassportStore implements ChangePassportStore {
    private static final int MAXIMUM_PASSPORT_BYTES = 1024 * 1024;
    private static final int MAXIMUM_POINTER_BYTES = 4096;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final ChangePassportPaths paths;
    private final MarkdownChangePassportRenderer renderer;
    private final Clock clock;
    private final MoveOperation mover;

    public FileSystemChangePassportStore(ChangePassportPaths paths, MarkdownChangePassportRenderer renderer, Clock clock) {
        this(paths, renderer, clock, Files::move);
    }

    FileSystemChangePassportStore(ChangePassportPaths paths, MarkdownChangePassportRenderer renderer, Clock clock,
            MoveOperation mover) {
        this.paths = java.util.Objects.requireNonNull(paths, "paths");
        this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.mover = java.util.Objects.requireNonNull(mover, "mover");
    }
    @Override public Path save(ChangePassport passport) {
        byte[] data = strictUtf8(renderer.render(java.util.Objects.requireNonNull(passport, "passport")));
        if (data.length > MAXIMUM_PASSPORT_BYTES) throw ChangePassportPersistenceException.saveFailure();
        Path artifactTemp = null, pointerTemp = null, destination = null;
        boolean artifactPublished = false;
        try {
            prepare(); destination = destination(Instant.now(clock));
            artifactTemp = Files.createTempFile(paths.passportsDirectory(), ".passport-", ".tmp");
            Files.write(artifactTemp, data, StandardOpenOption.TRUNCATE_EXISTING);
            moveArtifactWithoutReplacing(artifactTemp, destination);
            artifactTemp = null;
            artifactPublished = true;
            byte[] pointer = strictUtf8(destination.toAbsolutePath().normalize() + "\n");
            if (pointer.length > MAXIMUM_POINTER_BYTES) throw new IOException("pointer too large");
            if (Files.exists(paths.latestPointer(), LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(paths.latestPointer())) throw new IOException("unsafe pointer");
            pointerTemp = Files.createTempFile(paths.rootDirectory(), ".latest-passport-", ".tmp");
            Files.write(pointerTemp, pointer, StandardOpenOption.TRUNCATE_EXISTING);
            move(pointerTemp, paths.latestPointer(), true); pointerTemp = null;
            return destination.toAbsolutePath().normalize();
        } catch (IOException | RuntimeException exception) {
            delete(artifactTemp);
            delete(pointerTemp);
            if (artifactPublished) delete(destination);
            throw ChangePassportPersistenceException.saveFailure();
        }
    }
    @Override public Optional<StoredPassportIdentity> readLatestIdentity() {
        try {
            if (!Files.exists(paths.rootDirectory(), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            directory(paths.rootDirectory());
            if (!Files.exists(paths.latestPointer(), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            directory(paths.passportsDirectory()); regular(paths.latestPointer());
            Path passport = pointer(read(paths.latestPointer(), MAXIMUM_POINTER_BYTES));
            validateContainedArtifact(passport); regular(passport);
            return Optional.of(parse(passport, read(passport, MAXIMUM_PASSPORT_BYTES)));
        } catch (IOException | RuntimeException exception) { throw ChangePassportPersistenceException.readFailure(); }
    }
    private StoredPassportIdentity parse(Path artifact, String markdown) throws IOException {
        String prefix = "<!-- codedefense-change-passport:v1;";
        String suffix = " -->";
        String metadata = null;
        for (String line : markdown.split("\\n", -1)) {
            if (line.startsWith(prefix)) {
                if (metadata != null || !line.endsWith(suffix)) throw new IOException("invalid metadata");
                metadata = line.substring(prefix.length(), line.length() - suffix.length());
            }
        }
        if (metadata == null) throw new IOException("invalid metadata");
        String[] fields = metadata.split(";", -1);
        if (fields.length != 6 || !fields[0].startsWith("root=") || !fields[1].startsWith("base=")
                || !fields[2].startsWith("tree=") || !fields[3].startsWith("diff=")
                || !fields[4].startsWith("paths=") || !fields[5].startsWith("timestamp=")) {
            throw new IOException("invalid metadata");
        }
        try { return new StoredPassportIdentity(artifact, fields[0].substring(5), fields[1].substring(5), fields[2].substring(5), fields[3].substring(5), java.util.List.of(fields[4].substring(6).split(",")), Instant.parse(fields[5].substring(10))); }
        catch (IllegalArgumentException exception) { throw new IOException("invalid metadata", exception); }
    }
    private Path pointer(String value) throws IOException {
        if (!value.endsWith("\n") || value.indexOf('\r') >= 0) throw new IOException("invalid pointer");
        String raw = value.substring(0, value.length() - 1); if (raw.isBlank() || raw.indexOf('\n') >= 0) throw new IOException("invalid pointer");
        Path path = Path.of(raw); if (!path.isAbsolute() || !path.equals(path.normalize()) || !path.getFileName().toString().endsWith(".md")) throw new IOException("unsafe pointer");
        path = path.toAbsolutePath().normalize();
        if (!path.startsWith(paths.passportsDirectory())) throw new IOException("pointer escapes storage");
        return path;
    }
    private void validateContainedArtifact(Path artifact) throws IOException {
        Path passportRoot = paths.passportsDirectory().toAbsolutePath().normalize();
        directory(passportRoot);
        Path realRoot = passportRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path relative = passportRoot.relativize(artifact);
        Path current = passportRoot;
        for (int index = 0; index < relative.getNameCount() - 1; index++) {
            current = current.resolve(relative.getName(index));
            directory(current);
            if (!current.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(realRoot)) {
                throw new IOException("artifact escapes storage");
            }
        }
    }
    private void prepare() throws IOException { ensure(paths.rootDirectory()); ensure(paths.passportsDirectory()); }
    private static void ensure(Path path) throws IOException { if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { directory(path); } else Files.createDirectory(path); }
    private static void directory(Path path) throws IOException { if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("unsafe directory"); }
    private static void regular(Path path) throws IOException { if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("unsafe file"); }
    private Path destination(Instant time) throws IOException { String base = FILE_TIME.format(time) + "-change-passport"; for (int i=1;i<Integer.MAX_VALUE;i++) { Path candidate = paths.passportsDirectory().resolve(base + (i == 1 ? "" : "-" + i) + ".md").normalize(); if (candidate.startsWith(paths.passportsDirectory()) && !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate; } throw new IOException("no name"); }
    private void move(Path from, Path to, boolean replace) throws IOException {
        try {
            if (replace) mover.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else mover.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            if (replace) mover.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            else mover.move(from, to);
        }
    }
    private void moveArtifactWithoutReplacing(Path from, Path to) throws IOException {
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) throw new IOException("destination already exists");
        mover.move(from, to);
    }
    private static byte[] strictUtf8(String value) { try { ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value)); byte[] result = new byte[bytes.remaining()]; bytes.get(result); return result; } catch (CharacterCodingException exception) { throw ChangePassportPersistenceException.saveFailure(); } }
    private static String read(Path path, int limit) throws IOException { byte[] bytes = bounded(path, limit); try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); } catch (CharacterCodingException exception) { throw new IOException("invalid UTF-8", exception); } }
    private static byte[] bounded(Path path, int limit) throws IOException { try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] buffer = new byte[4096]; int size=0, read; while ((read=in.read(buffer)) >= 0) { size += read; if (size > limit) throw new IOException("too large"); out.write(buffer, 0, read); } return out.toByteArray(); } }
    private static void delete(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { } }

    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path target, java.nio.file.CopyOption... options) throws IOException;
    }
}
