package dev.codedefense.change;

import dev.codedefense.ai.ProcessExecutor;
import dev.codedefense.ai.ProcessResult;
import dev.codedefense.ai.ProcessSpec;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.scanner.ProjectFileFilter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Captures changed objects directly from Git's index and HEAD. No changed path is ever resolved
 * against the working tree.
 */
public final class GitCliStagedChangeSource implements StagedChangeSource {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TERMINATION_GRACE_PERIOD = Duration.ofSeconds(1);
    private static final int DEFAULT_MAXIMUM_BLOB_BYTES = 24 * 1024;
    private static final int DEFAULT_MAXIMUM_DIFF_BYTES = 120 * 1024;
    private static final String ZERO_OBJECT_ID = "0".repeat(40);

    private final ProcessExecutor processExecutor;
    private final ProjectFileFilter fileFilter;
    private final int maximumBlobBytes;
    private final int maximumDiffBytes;

    public GitCliStagedChangeSource(ProcessExecutor processExecutor) {
        this(processExecutor, new ProjectFileFilter(), DEFAULT_MAXIMUM_BLOB_BYTES, DEFAULT_MAXIMUM_DIFF_BYTES);
    }

    GitCliStagedChangeSource(ProcessExecutor processExecutor, int maximumBlobBytes, int maximumDiffBytes) {
        this(processExecutor, new ProjectFileFilter(), maximumBlobBytes, maximumDiffBytes);
    }

    GitCliStagedChangeSource(
            ProcessExecutor processExecutor,
            ProjectFileFilter fileFilter,
            int maximumBlobBytes,
            int maximumDiffBytes) {
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.fileFilter = Objects.requireNonNull(fileFilter, "fileFilter");
        if (maximumBlobBytes <= 0 || maximumDiffBytes <= 0) {
            throw new IllegalArgumentException("Git capture limits must be positive");
        }
        this.maximumBlobBytes = maximumBlobBytes;
        this.maximumDiffBytes = maximumDiffBytes;
    }

    @Override
    public CapturedStagedChange capture(Path requestedPath) {
        Path requestedRoot = normalizeDirectory(requestedPath);
        Path root = resolveRepositoryRoot(requestedRoot);
        String baseCommit = requiredText(run(root, 256, "rev-parse", "--verify", "HEAD"));
        String indexTree = requiredText(run(root, 256, "write-tree"));
        requireObjectId(baseCommit);
        requireObjectId(indexTree);

        List<RawEntry> rawEntries = parseRaw(requiredBytes(run(root, maximumDiffBytes,
                "diff", "--cached", "--raw", "-z", "--no-abbrev", "--find-renames=100%")));
        if (rawEntries.isEmpty()) {
            throw new GitChangeException(GitChangeException.Kind.NO_STAGED_CHANGE);
        }
        Map<Path, LineCounts> lineCounts = parseNumstat(requiredBytes(run(root, maximumDiffBytes,
                "diff", "--cached", "--numstat", "-z", "--no-ext-diff")));
        ProcessResult canonicalDiffResult = run(root, maximumDiffBytes,
                "diff", "--cached", "--no-ext-diff", "--no-color", "--full-index", "--binary");
        String canonicalDiff = decodeCommandText(requiredBytes(canonicalDiffResult), canonicalDiffResult.stdoutTruncated());

        List<EntryWithFile> files = new ArrayList<>();
        for (RawEntry entry : rawEntries) {
            LineCounts counts = lineCounts.getOrDefault(entry.path(), LineCounts.ZERO);
            StagedChangeFile file;
            try {
                file = new StagedChangeFile(entry.path(), entry.status(), counts.added(), counts.deleted());
            } catch (IllegalArgumentException exception) {
                throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
            }
            files.add(new EntryWithFile(entry, file, counts.binary()));
        }
        files.sort(Comparator.comparing(value -> portable(value.file().path())));
        List<StagedChangeFile> changedFiles = files.stream().map(EntryWithFile::file).toList();
        int addedLines = changedFiles.stream().mapToInt(StagedChangeFile::addedLines).sum();
        int deletedLines = changedFiles.stream().mapToInt(StagedChangeFile::deletedLines).sum();
        StagedChange change = new StagedChange(
                root,
                sha256(root.toString()),
                baseCommit,
                indexTree,
                sha256("codedefense-staged-change-v1\0" + baseCommit + "\0" + indexTree),
                changedFiles,
                addedLines,
                deletedLines);

        List<IndexBlob> blobs = new ArrayList<>();
        for (EntryWithFile value : files) {
            if (!isEligible(value)) {
                continue;
            }
            Optional<DecodedBlob> index = readBlob(root, value.entry().newObjectId());
            Optional<DecodedBlob> base = readBlob(root, value.entry().oldObjectId());
            if (index.isEmpty() && base.isEmpty()) {
                continue;
            }
            blobs.add(new IndexBlob(
                    value.file(),
                    index.map(DecodedBlob::content), index.map(DecodedBlob::truncated).orElse(false),
                    base.map(DecodedBlob::content), base.map(DecodedBlob::truncated).orElse(false)));
        }
        return new CapturedStagedChange(change, blobs, canonicalDiff);
    }

    private Path normalizeDirectory(Path requestedPath) {
        Objects.requireNonNull(requestedPath, "requestedPath");
        Path root = requestedPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new GitChangeException(GitChangeException.Kind.INVALID_REPOSITORY);
        }
        return root;
    }

    private Path resolveRepositoryRoot(Path requestedRoot) {
        ProcessResult result = run(requestedRoot, 4096, "rev-parse", "--show-toplevel");
        String output = requiredText(result);
        Path root = Path.of(output).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new GitChangeException(GitChangeException.Kind.INVALID_REPOSITORY);
        }
        return root;
    }

    private ProcessResult run(Path root, int outputLimit, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        ProcessResult result;
        try {
            result = processExecutor.execute(new ProcessSpec(
                    command, root, Map.of(), "", TIMEOUT, TERMINATION_GRACE_PERIOD, outputLimit, 4096));
        } catch (RuntimeException exception) {
            if (exception instanceof GitChangeException gitChangeException) {
                throw gitChangeException;
            }
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
        if (result.timedOut() || result.stderrTruncated()) {
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
        if (result.exitCode() != 0) {
            if (arguments.length == 2 && arguments[0].equals("rev-parse")
                    && arguments[1].equals("--show-toplevel")) {
                throw new GitChangeException(GitChangeException.Kind.INVALID_REPOSITORY);
            }
            if (arguments.length == 3 && arguments[0].equals("rev-parse")
                    && arguments[1].equals("--verify") && arguments[2].equals("HEAD")) {
                throw new GitChangeException(GitChangeException.Kind.NO_HEAD);
            }
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
        if (result.stdoutTruncated() && !isBlobCommand(arguments)) {
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
        return result;
    }

    private Optional<DecodedBlob> readBlob(Path root, String objectId) {
        if (isZeroObjectId(objectId)) {
            return Optional.empty();
        }
        ProcessResult result = run(root, maximumBlobBytes, "cat-file", "blob", objectId);
        byte[] bytes = result.stdoutBytes();
        if (containsNul(bytes)) {
            return Optional.empty();
        }
        return Optional.of(new DecodedBlob(decodeBlob(bytes, result.stdoutTruncated()), result.stdoutTruncated()));
    }

    private boolean isEligible(EntryWithFile value) {
        RawEntry entry = value.entry();
        if (value.binary() || !entry.isEligibleRegularFile() || hasExcludedDirectory(entry.path())) {
            return false;
        }
        return !fileFilter.isExcludedFile(entry.path()) && fileFilter.isSupportedFile(entry.path());
    }

    private boolean hasExcludedDirectory(Path path) {
        for (Path component : path) {
            if (fileFilter.isExcludedDirectory(component)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlobCommand(String[] arguments) {
        return arguments.length >= 2 && arguments[0].equals("cat-file") && arguments[1].equals("blob");
    }

    private static String requiredText(ProcessResult result) {
        return decodeCommandText(requiredBytes(result), false).trim();
    }

    private static byte[] requiredBytes(ProcessResult result) {
        if (result.stdoutTruncated()) {
            throw new GitChangeException(GitChangeException.Kind.EXECUTION_FAILED);
        }
        return result.stdoutBytes();
    }

    private static String decodeCommandText(byte[] bytes, boolean truncated) {
        try {
            return decodeUtf8(bytes, bytes.length);
        } catch (CharacterCodingException exception) {
            if (truncated) {
                for (int trim = 1; trim <= 3 && bytes.length - trim >= 0; trim++) {
                    try {
                        return decodeUtf8(bytes, bytes.length - trim);
                    } catch (CharacterCodingException ignored) {
                        // Keep trying only the possible UTF-8 trailing sequence lengths.
                    }
                }
            }
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static String decodeBlob(byte[] bytes, boolean truncated) {
        return decodeCommandText(bytes, truncated);
    }

    private static String decodeUtf8(byte[] bytes, int length) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, length))
                .toString();
    }

    private static List<RawEntry> parseRaw(byte[] bytes) {
        List<String> fields = nulFields(bytes);
        List<RawEntry> entries = new ArrayList<>();
        int index = 0;
        while (index < fields.size()) {
            String header = fields.get(index++);
            if (header.isEmpty()) {
                throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
            }
            Header parsed = parseHeader(header);
            if (index >= fields.size()) {
                throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
            }
            Path oldPath = safePath(fields.get(index++));
            Path path = oldPath;
            if (parsed.status() == StagedFileStatus.RENAMED) {
                if (index >= fields.size()) {
                    throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
                }
                path = safePath(fields.get(index++));
            }
            entries.add(new RawEntry(parsed.oldMode(), parsed.newMode(), parsed.oldObjectId(), parsed.newObjectId(),
                    parsed.status(), path));
        }
        return entries;
    }

    private static Header parseHeader(String header) {
        if (!header.startsWith(":")) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
        String[] values = header.substring(1).split(" ");
        if (values.length != 5) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
        requireMode(values[0]);
        requireMode(values[1]);
        requireObjectIdOrZero(values[2]);
        requireObjectIdOrZero(values[3]);
        StagedFileStatus status = switch (values[4].charAt(0)) {
            case 'A' -> StagedFileStatus.ADDED;
            case 'M', 'T' -> StagedFileStatus.MODIFIED;
            case 'D' -> StagedFileStatus.DELETED;
            case 'R' -> StagedFileStatus.RENAMED;
            default -> throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        };
        return new Header(values[0], values[1], values[2], values[3], status);
    }

    private static Map<Path, LineCounts> parseNumstat(byte[] bytes) {
        List<String> fields = nulFields(bytes);
        Map<Path, LineCounts> counts = new HashMap<>();
        int index = 0;
        while (index < fields.size()) {
            String field = fields.get(index++);
            if (field.isEmpty()) {
                throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
            }
            String[] parts = field.split("\\t", -1);
            if (parts.length != 3) {
                throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
            }
            boolean binary = parts[0].equals("-") || parts[1].equals("-");
            int added = binary ? 0 : parseCount(parts[0]);
            int deleted = binary ? 0 : parseCount(parts[1]);
            Path path;
            if (parts[2].isEmpty()) {
                if (index + 1 >= fields.size()) {
                    throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
                }
                safePath(fields.get(index++));
                path = safePath(fields.get(index++));
            } else {
                path = safePath(parts[2]);
            }
            counts.put(path, new LineCounts(added, deleted, binary));
        }
        return counts;
    }

    private static List<String> nulFields(byte[] bytes) {
        String decoded;
        try {
            decoded = decodeUtf8(bytes, bytes.length);
        } catch (CharacterCodingException exception) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
        if (decoded.isEmpty()) {
            return List.of();
        }
        if (decoded.charAt(decoded.length() - 1) != '\0') {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
        return List.of(decoded.substring(0, decoded.length() - 1).split("\0", -1));
    }

    private static Path safePath(String value) {
        try {
            if (value.isEmpty() || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException();
            }
            return Path.of(value);
        } catch (RuntimeException exception) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
    }

    private static int parseCount(String value) {
        try {
            int count = Integer.parseInt(value);
            if (count < 0) {
                throw new NumberFormatException();
            }
            return count;
        } catch (NumberFormatException exception) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
    }

    private static void requireMode(String mode) {
        if (!mode.matches("[0-7]{6}")) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
    }

    private static void requireObjectId(String objectId) {
        if (!objectId.matches("[0-9a-f]{40,64}")) {
            throw new GitChangeException(GitChangeException.Kind.MALFORMED_DATA);
        }
    }

    private static void requireObjectIdOrZero(String objectId) {
        if (!isZeroObjectId(objectId)) {
            requireObjectId(objectId);
        }
    }

    private static boolean isZeroObjectId(String objectId) {
        return objectId.matches("0{40,64}");
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Header(String oldMode, String newMode, String oldObjectId, String newObjectId, StagedFileStatus status) {
    }

    private record RawEntry(
            String oldMode, String newMode, String oldObjectId, String newObjectId, StagedFileStatus status, Path path) {
        private boolean isEligibleRegularFile() {
            if (oldMode.equals("120000") || newMode.equals("120000")
                    || oldMode.equals("160000") || newMode.equals("160000")) {
                return false;
            }
            return switch (status) {
                case ADDED, MODIFIED, RENAMED -> newMode.startsWith("100");
                case DELETED -> oldMode.startsWith("100");
            };
        }
    }

    private record LineCounts(int added, int deleted, boolean binary) {
        private static final LineCounts ZERO = new LineCounts(0, 0, false);
    }

    private record EntryWithFile(RawEntry entry, StagedChangeFile file, boolean binary) {
    }

    private record DecodedBlob(String content, boolean truncated) {
    }
}
