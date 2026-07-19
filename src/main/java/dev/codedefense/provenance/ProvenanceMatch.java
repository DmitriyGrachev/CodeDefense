package dev.codedefense.provenance;

import dev.codedefense.domain.CodexProvenanceStatus;
import java.util.List;
import java.util.Objects;

public record ProvenanceMatch(CodexProvenanceStatus status, int selectedFileCount,
        List<String> matchedRelativePaths) {
    public ProvenanceMatch {
        Objects.requireNonNull(status, "status");
        if (status == CodexProvenanceStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("matcher cannot produce unavailable");
        }
        if (selectedFileCount < 1) throw new IllegalArgumentException("selectedFileCount must be positive");
        Objects.requireNonNull(matchedRelativePaths, "matchedRelativePaths");
        matchedRelativePaths = matchedRelativePaths.stream().sorted().distinct().toList();
        int matched = matchedRelativePaths.size();
        if (matched > selectedFileCount
                || status == CodexProvenanceStatus.EXACT_CHANGE_MATCH && matched != selectedFileCount
                || status == CodexProvenanceStatus.PARTIAL_PATH_MATCH && (matched == 0 || matched >= selectedFileCount)
                || status == CodexProvenanceStatus.NO_MATCH && matched != 0) {
            throw new IllegalArgumentException("provenance match counts are inconsistent");
        }
    }

    public int matchedFileCount() { return matchedRelativePaths.size(); }
    @Override public String toString() {
        return "ProvenanceMatch[status=%s, selectedFileCount=%d, matchedFileCount=%d]"
                .formatted(status, selectedFileCount, matchedFileCount());
    }
}
