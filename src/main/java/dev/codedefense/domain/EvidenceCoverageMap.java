package dev.codedefense.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EvidenceCoverageMap(String diffFingerprint, List<EvidenceCoverageHunk> hunks) {
    private static final List<String> QUESTION_ORDER =
            List.of("decision", "counterfactual", "test-prediction");

    public EvidenceCoverageMap {
        Objects.requireNonNull(diffFingerprint, "diffFingerprint");
        if (!diffFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("diffFingerprint is invalid");
        }
        Objects.requireNonNull(hunks, "hunks");
        hunks = List.copyOf(hunks);
        if (hunks.size() > 256 || hunks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Coverage hunks are invalid");
        }
        Set<String> identities = new HashSet<>();
        for (EvidenceCoverageHunk hunk : hunks) {
            if (!identities.add(hunk.relativePath() + "\0" + hunk.ordinal())) {
                throw new IllegalArgumentException("Coverage hunk identities must be unique");
            }
        }
    }

    public EvidenceCoverageSummary summary() {
        int measurable = (int) hunks.stream()
                .filter(value -> value.state() != EvidenceCoverageState.UNMEASURABLE).count();
        int referenced = (int) hunks.stream()
                .filter(value -> value.state() == EvidenceCoverageState.REFERENCED).count();
        return new EvidenceCoverageSummary(hunks.size(), measurable, referenced);
    }

    public EvidenceCoverageMap cumulativeThrough(String questionId) {
        int end = QUESTION_ORDER.indexOf(Objects.requireNonNull(questionId, "questionId"));
        if (end < 0) throw new IllegalArgumentException("Unknown question category");
        Set<String> visible = Set.copyOf(QUESTION_ORDER.subList(0, end + 1));
        return new EvidenceCoverageMap(diffFingerprint, hunks.stream().map(hunk -> {
            if (hunk.state() == EvidenceCoverageState.UNMEASURABLE) return hunk;
            List<String> categories = hunk.categoryIds().stream().filter(visible::contains).toList();
            return new EvidenceCoverageHunk(hunk.relativePath(), hunk.ordinal(), hunk.startLine(), hunk.endLine(),
                    hunk.navigable(), categories.isEmpty() ? EvidenceCoverageState.UNREFERENCED
                            : EvidenceCoverageState.REFERENCED, categories);
        }).toList());
    }

    @Override public String toString() {
        return "EvidenceCoverageMap[diffFingerprint=%s, summary=%s]".formatted(diffFingerprint, summary());
    }
}
