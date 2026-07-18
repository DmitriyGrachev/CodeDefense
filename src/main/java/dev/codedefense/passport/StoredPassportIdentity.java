package dev.codedefense.passport;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.StagedChangeFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Parsed source-free identity envelope for deciding whether a passport is current. */
public record StoredPassportIdentity(Path passport, String repositoryIdentityHash, String baseCommit,
        String indexTree, String diffFingerprint, List<String> changedPathHashes, Instant createdAt) {
    public StoredPassportIdentity {
        Objects.requireNonNull(passport, "passport");
        passport = passport.toAbsolutePath().normalize();
        repositoryIdentityHash = requireHash(repositoryIdentityHash);
        baseCommit = requireGitId(baseCommit);
        indexTree = requireGitId(indexTree);
        diffFingerprint = requireHash(diffFingerprint);
        Objects.requireNonNull(changedPathHashes, "changedPathHashes");
        changedPathHashes = List.copyOf(changedPathHashes);
        String previous = null;
        for (String hash : changedPathHashes) {
            requireHash(hash);
            if (previous != null && previous.compareTo(hash) >= 0) throw new IllegalArgumentException("Path hashes must be sorted and unique");
            previous = hash;
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static StoredPassportIdentity from(ChangePassport passport, Path path) {
        return new StoredPassportIdentity(path, passport.change().repositoryIdentityHash(), passport.change().baseCommit(),
                passport.change().indexTree(), passport.change().diffFingerprint(),
                passport.change().files().stream().map(StagedChangeFile::path).map(StoredPassportIdentity::pathHash).sorted().toList(),
                passport.createdAt());
    }

    public static String pathHash(Path path) {
        return sha256(path.toString().replace('\\', '/'));
    }
    static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String requireHash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid identity hash"); return value; }
    private static String requireGitId(String value) { if (value == null || !value.matches("[0-9a-f]{40,64}")) throw new IllegalArgumentException("Invalid Git object id"); return value; }
}
