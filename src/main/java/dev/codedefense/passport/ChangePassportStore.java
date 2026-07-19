package dev.codedefense.passport;

import dev.codedefense.domain.ChangePassport;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;

public interface ChangePassportStore {
    Path save(ChangePassport passport);
    Optional<StoredPassportIdentity> readLatestIdentity();
    default Optional<StoredChangePassport> readLatest() { return Optional.empty(); }
    default List<StoredChangePassport> list(int limit) { return List.of(); }
    default List<StoredChangePassport> listByFingerprint(String fingerprint, int limit) {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint is invalid");
        }
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit must be between 1 and 20");
        return list(50).stream().filter(value -> value.receipt().diffFingerprint().equals(fingerprint))
                .limit(limit).toList();
    }
}
