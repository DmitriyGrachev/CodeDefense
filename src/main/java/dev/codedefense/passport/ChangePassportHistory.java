package dev.codedefense.passport;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Validated newest-first history for one repository and one exact diff fingerprint. */
public record ChangePassportHistory(List<StoredChangePassport> attempts) {
    public ChangePassportHistory {
        Objects.requireNonNull(attempts, "attempts");
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty() || attempts.size() > 20 || attempts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("history must contain between 1 and 20 attempts");
        }
        String fingerprint = attempts.getFirst().receipt().diffFingerprint();
        String repository = attempts.getFirst().receipt().repositoryIdentityHash();
        HashSet<String> ids = new HashSet<>();
        for (StoredChangePassport stored : attempts) {
            var receipt = stored.receipt();
            if (!fingerprint.equals(receipt.diffFingerprint())
                    || !repository.equals(receipt.repositoryIdentityHash())
                    || !ids.add(receipt.attemptId().value())) {
                throw new IllegalArgumentException("history identity is inconsistent");
            }
        }
        List<StoredChangePassport> chronological = attempts.reversed();
        for (int index = 0; index < chronological.size(); index++) {
            var receipt = chronological.get(index).receipt();
            if (receipt.attemptNumber() != index + 1) throw new IllegalArgumentException("attempt numbers are not contiguous");
            if (index > 0 && !receipt.supersedes().orElseThrow()
                    .equals(chronological.get(index - 1).receipt().attemptId())) {
                throw new IllegalArgumentException("attempt lineage is broken");
            }
        }
    }
}
