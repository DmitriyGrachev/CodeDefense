package dev.codedefense.domain;

import java.time.Instant;
import java.util.*;

public record ChangeHandoff(int schemaVersion, String handoffId, Instant createdAt,
        String repositoryIdentityHash, ChangeKind changeKind, String baseCommit,
        String sourceIdentity, String diffFingerprint, List<PassportAttemptSummary> attempts,
        String payloadSha256) {
    public ChangeHandoff {
        if (schemaVersion != 1) throw new IllegalArgumentException("unsupported handoff schema");
        try { if (!UUID.fromString(handoffId).toString().equals(handoffId)) throw new IllegalArgumentException(); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("invalid handoff ID", exception); }
        Objects.requireNonNull(createdAt); Objects.requireNonNull(changeKind);
        requireHash(repositoryIdentityHash, 64, 64); requireHash(baseCommit, 40, 64);
        requireHash(sourceIdentity, changeKind == ChangeKind.STAGED ? 64 : 40, 64);
        requireHash(diffFingerprint, 64, 64); requireHash(payloadSha256, 64, 64);
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty() || attempts.size() > 20 || attempts.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("attempt history is invalid");
        Set<PassportAttemptId> ids = new HashSet<>();
        for (int index = 0; index < attempts.size(); index++) {
            PassportAttemptSummary attempt = attempts.get(index);
            if (!attempt.diffFingerprint().equals(diffFingerprint) || attempt.attemptNumber() != index + 1
                    || !ids.add(attempt.attemptId()) || index > 0 && !attempt.supersedes().orElseThrow()
                    .equals(attempts.get(index - 1).attemptId())) throw new IllegalArgumentException("attempt lineage is invalid");
        }
    }
    private static void requireHash(String value, int min, int max) {
        if (value == null || value.length() < min || value.length() > max || !value.matches("[0-9a-f]+"))
            throw new IllegalArgumentException("invalid hash");
    }
    @Override public String toString() {
        return "ChangeHandoff[handoffId=%s, kind=%s, attemptCount=%d, fingerprint=%s]"
                .formatted(handoffId, changeKind, attempts.size(), diffFingerprint);
    }
}
