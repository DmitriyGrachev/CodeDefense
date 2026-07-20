package dev.codedefense.passport;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.ChangeKind;
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
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.Predicate;

/** No-follow-link filesystem store for source-free Markdown Change Passports. */
public final class FileSystemChangePassportStore implements ChangePassportStore {
    private static final int MAXIMUM_PASSPORT_BYTES = 1024 * 1024;
    private static final int MAXIMUM_POINTER_BYTES = 4096;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final ChangePassportPaths paths;
    private final MarkdownChangePassportRenderer renderer;
    private final PassportReceiptJsonCodec receiptCodec;
    private final EvidenceCoverageSidecarCodec coverageCodec = new EvidenceCoverageSidecarCodec();
    private final Clock clock;
    private final Supplier<String> receiptIdGenerator;
    private final MoveOperation mover;

    public FileSystemChangePassportStore(ChangePassportPaths paths, MarkdownChangePassportRenderer renderer, Clock clock) {
        this(paths, renderer, new PassportReceiptJsonCodec(), clock,
                () -> java.util.UUID.randomUUID().toString(), Files::move);
    }

    FileSystemChangePassportStore(ChangePassportPaths paths, MarkdownChangePassportRenderer renderer, Clock clock,
            MoveOperation mover) {
        this(paths, renderer, new PassportReceiptJsonCodec(), clock,
                () -> java.util.UUID.randomUUID().toString(), mover);
    }

    FileSystemChangePassportStore(ChangePassportPaths paths, MarkdownChangePassportRenderer renderer,
            PassportReceiptJsonCodec receiptCodec, Clock clock, Supplier<String> receiptIdGenerator) {
        this(paths, renderer, receiptCodec, clock, receiptIdGenerator, Files::move);
    }

    FileSystemChangePassportStore(ChangePassportPaths paths, MarkdownChangePassportRenderer renderer,
            PassportReceiptJsonCodec receiptCodec, Clock clock, Supplier<String> receiptIdGenerator,
            MoveOperation mover) {
        this.paths = java.util.Objects.requireNonNull(paths, "paths");
        this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
        this.receiptCodec = java.util.Objects.requireNonNull(receiptCodec, "receiptCodec");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.receiptIdGenerator = java.util.Objects.requireNonNull(receiptIdGenerator, "receiptIdGenerator");
        this.mover = java.util.Objects.requireNonNull(mover, "mover");
    }
    @Override public Path save(ChangePassport passport) {
        byte[] data = strictUtf8(renderer.render(java.util.Objects.requireNonNull(passport, "passport")));
        if (data.length > MAXIMUM_PASSPORT_BYTES) throw ChangePassportPersistenceException.saveFailure();
        String receiptId = receiptIdGenerator.get();
        var initialReceipt = dev.codedefense.domain.PassportReceipt.from(passport, receiptId);
        var previous = listByFingerprint(initialReceipt.diffFingerprint(), 20).stream().findFirst();
        var receipt = previous.map(value -> new dev.codedefense.domain.PassportReceipt(
                initialReceipt.schemaVersion(), receiptId, initialReceipt.repositoryIdentityHash(), initialReceipt.changeKind(),
                initialReceipt.baseCommit(), initialReceipt.sourceIdentity(), initialReceipt.diffFingerprint(),
                initialReceipt.createdAt(), initialReceipt.statusAtCreation(), initialReceipt.files(),
                initialReceipt.categories(), initialReceipt.overallScore(), initialReceipt.readiness(),
                initialReceipt.skippedPrimaryCount(), initialReceipt.model(),
                new dev.codedefense.domain.PassportAttemptId(receiptId),
                java.util.Optional.of(value.receipt().attemptId()), value.receipt().attemptNumber() + 1,
                initialReceipt.focus(), initialReceipt.codexProvenance(),
                initialReceipt.evidenceCoverage())).orElse(initialReceipt);
        byte[] receiptData = receiptCodec.encode(receipt);
        Path artifactTemp = null, receiptTemp = null, pointerTemp = null, destination = null,
                receiptDestination = null;
        boolean artifactPublished = false, receiptPublished = false;
        try {
            prepare(); destination = destination(Instant.now(clock));
            receiptDestination = sidecar(destination);
            artifactTemp = Files.createTempFile(paths.passportsDirectory(), ".passport-", ".tmp");
            Files.write(artifactTemp, data, StandardOpenOption.TRUNCATE_EXISTING);
            receiptTemp = Files.createTempFile(paths.passportsDirectory(), ".receipt-", ".tmp");
            Files.write(receiptTemp, receiptData, StandardOpenOption.TRUNCATE_EXISTING);
            move(artifactTemp, destination, false);
            artifactTemp = null;
            artifactPublished = true;
            move(receiptTemp, receiptDestination, false);
            receiptTemp = null;
            receiptPublished = true;
            byte[] pointer = strictUtf8(destination.toAbsolutePath().normalize() + "\n");
            if (pointer.length > MAXIMUM_POINTER_BYTES) throw new IOException("pointer too large");
            if (Files.exists(paths.latestPointer(), LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(paths.latestPointer())) throw new IOException("unsafe pointer");
            pointerTemp = Files.createTempFile(paths.rootDirectory(), ".latest-passport-", ".tmp");
            Files.write(pointerTemp, pointer, StandardOpenOption.TRUNCATE_EXISTING);
            move(pointerTemp, paths.latestPointer(), true); pointerTemp = null;
            if (passport.evidenceCoverage().isPresent()) {
                writeCoverageBestEffort(new StoredEvidenceCoverage(receipt.receiptId(),
                        passport.evidenceCoverage().orElseThrow()), destination);
            }
            return destination.toAbsolutePath().normalize();
        } catch (IOException | RuntimeException exception) {
            delete(artifactTemp);
            delete(receiptTemp);
            delete(pointerTemp);
            if (artifactPublished) delete(destination);
            if (receiptPublished) delete(receiptDestination);
            throw ChangePassportPersistenceException.saveFailure();
        }
    }

    @Override public Optional<StoredChangePassport> readLatest() {
        try {
            if (!Files.exists(paths.rootDirectory(), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            directory(paths.rootDirectory());
            if (!Files.exists(paths.latestPointer(), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            directory(paths.passportsDirectory()); regular(paths.latestPointer());
            Path markdown = pointer(read(paths.latestPointer(), MAXIMUM_POINTER_BYTES));
            validateContainedArtifact(markdown); regular(markdown);
            Path receipt = sidecar(markdown);
            if (!Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            regular(receipt);
            return Optional.of(stored(markdown, receipt));
        } catch (IOException | RuntimeException exception) {
            throw ChangePassportPersistenceException.readFailure();
        }
    }

    @Override public Optional<StoredEvidenceCoverage> readLatestCoverage() {
        try {
            Optional<StoredChangePassport> latest = readLatest();
            if (latest.isEmpty()) return Optional.empty();
            StoredChangePassport passport = latest.orElseThrow();
            Path path = coverageSidecar(passport.markdownPath());
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            validateContainedArtifact(path);
            StoredEvidenceCoverage coverage = coverageCodec.decode(bounded(path,
                    EvidenceCoverageSidecarCodec.MAXIMUM_BYTES));
            if (!coverage.receiptId().equals(passport.receipt().receiptId())
                    || !coverage.coverage().diffFingerprint().equals(passport.receipt().diffFingerprint())) {
                return Optional.empty();
            }
            return Optional.of(coverage);
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    @Override public List<StoredChangePassport> list(int limit) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        return listMatching(limit, receipt -> true);
    }
    @Override public List<StoredChangePassport> listByRepository(String repositoryIdentityHash, int limit) {
        if (repositoryIdentityHash == null || !repositoryIdentityHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("repositoryIdentityHash is invalid");
        }
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        return listMatching(limit, receipt -> receipt.repositoryIdentityHash().equals(repositoryIdentityHash));
    }
    @Override public List<StoredChangePassport> listByRepositoryAndKind(String repositoryIdentityHash,
            ChangeKind changeKind, int limit) {
        if (repositoryIdentityHash == null || !repositoryIdentityHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("repositoryIdentityHash is invalid");
        }
        java.util.Objects.requireNonNull(changeKind, "changeKind");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        return listMatching(limit, receipt -> receipt.repositoryIdentityHash().equals(repositoryIdentityHash)
                && receipt.changeKind() == changeKind);
    }
    private List<StoredChangePassport> listMatching(int limit,
            Predicate<dev.codedefense.domain.PassportReceipt> filter) {
        try {
            if (!Files.exists(paths.rootDirectory(), LinkOption.NOFOLLOW_LINKS)) return List.of();
            directory(paths.rootDirectory());
            if (!Files.exists(paths.passportsDirectory(), LinkOption.NOFOLLOW_LINKS)) return List.of();
            directory(paths.passportsDirectory());
            try (var stream = Files.list(paths.passportsDirectory())) {
                List<Path> receipts = stream
                        .filter(path -> path.getFileName().toString()
                                .matches(".+-change-passport(?:-\\d+)?\\.json"))
                        .sorted(Comparator.comparing(FileSystemChangePassportStore::receiptTimeKey)
                                .thenComparingInt(FileSystemChangePassportStore::receiptOrdinal).reversed())
                        .toList();
                java.util.ArrayList<StoredChangePassport> result = new java.util.ArrayList<>();
                for (Path receipt : receipts) {
                    regular(receipt);
                    Path markdown = markdown(receipt);
                    regular(markdown);
                    StoredChangePassport value = stored(markdown, receipt);
                    if (filter.test(value.receipt())) result.add(value);
                    if (result.size() == limit) break;
                }
                return List.copyOf(result);
            }
        } catch (IOException | RuntimeException exception) {
            throw ChangePassportPersistenceException.readFailure();
        }
    }
    @Override public List<StoredChangePassport> listByFingerprint(String fingerprint, int limit) {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("fingerprint is invalid");
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit must be between 1 and 20");
        return list(50).stream().filter(value -> value.receipt().diffFingerprint().equals(fingerprint))
                .limit(limit).toList();
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
                || (!fields[2].startsWith("index=") && !fields[2].startsWith("tree=")) || !fields[3].startsWith("diff=")
                || !fields[4].startsWith("paths=") || !fields[5].startsWith("timestamp=")) {
            throw new IOException("invalid metadata");
        }
        boolean legacyTree = fields[2].startsWith("tree=");
        String indexIdentity = legacyTree ? "0".repeat(64) : fields[2].substring(6);
        try { return new StoredPassportIdentity(artifact, fields[0].substring(5), fields[1].substring(5), indexIdentity, fields[3].substring(5), java.util.List.of(fields[4].substring(6).split(",")), Instant.parse(fields[5].substring(10)), legacyTree); }
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
    private StoredChangePassport stored(Path markdown, Path receipt) throws IOException {
        return new StoredChangePassport(markdown.toAbsolutePath().normalize(),
                receipt.toAbsolutePath().normalize(), receiptCodec.decode(bounded(receipt,
                        PassportReceiptJsonCodec.MAXIMUM_RECEIPT_BYTES)));
    }
    private static Path sidecar(Path markdown) {
        String name = markdown.getFileName().toString();
        return markdown.resolveSibling(name.substring(0, name.length() - 2) + "json").normalize();
    }
    private static Path coverageSidecar(Path markdown) {
        String name = markdown.getFileName().toString();
        return markdown.resolveSibling(name.substring(0, name.length() - 3) + ".coverage.json").normalize();
    }
    private void writeCoverageBestEffort(StoredEvidenceCoverage coverage, Path markdown) {
        Path temp = null;
        Path destination = coverageSidecar(markdown);
        try {
            byte[] bytes = coverageCodec.encode(coverage);
            temp = Files.createTempFile(paths.passportsDirectory(), ".coverage-", ".tmp");
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            move(temp, destination, false);
            temp = null;
        } catch (RuntimeException | IOException exception) {
            delete(temp);
            delete(destination);
        }
    }
    private static Path markdown(Path receipt) {
        String name = receipt.getFileName().toString();
        return receipt.resolveSibling(name.substring(0, name.length() - 4) + "md").normalize();
    }
    private static String receiptTimeKey(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, Math.min(19, name.length()));
    }
    private static int receiptOrdinal(Path path) {
        String name = path.getFileName().toString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-change-passport(?:-(\\d+))?\\.json$").matcher(name);
        if (!matcher.find() || matcher.group(1) == null) return 1;
        try { return Integer.parseInt(matcher.group(1)); }
        catch (NumberFormatException exception) { return 1; }
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
    private static byte[] strictUtf8(String value) { try { ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value)); byte[] result = new byte[bytes.remaining()]; bytes.get(result); return result; } catch (CharacterCodingException exception) { throw ChangePassportPersistenceException.saveFailure(); } }
    private static String read(Path path, int limit) throws IOException { byte[] bytes = bounded(path, limit); try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); } catch (CharacterCodingException exception) { throw new IOException("invalid UTF-8", exception); } }
    private static byte[] bounded(Path path, int limit) throws IOException { try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] buffer = new byte[4096]; int size=0, read; while ((read=in.read(buffer)) >= 0) { size += read; if (size > limit) throw new IOException("too large"); out.write(buffer, 0, read); } return out.toByteArray(); } }
    private static void delete(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { } }

    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path target, java.nio.file.CopyOption... options) throws IOException;
    }
}
