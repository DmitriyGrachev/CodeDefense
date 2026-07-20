package dev.codedefense.change;

import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.EvidenceCoverageHunk;
import dev.codedefense.domain.EvidenceCoverageMap;
import dev.codedefense.domain.EvidenceCoverageState;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.SourceLineRange;
import dev.codedefense.domain.StagedFileStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvidenceCoverageCalculator {
    public EvidenceCoverageMap calculate(CapturedGitChange captured, ProjectAnalysis analysis) {
        Objects.requireNonNull(captured, "captured");
        Objects.requireNonNull(analysis, "analysis");
        Map<String, List<QuestionEvidence>> evidenceByPath = new LinkedHashMap<>();
        analysis.questions().forEach(question -> question.evidence().forEach(evidence ->
                evidenceByPath.computeIfAbsent(portable(evidence.path()), ignored -> new ArrayList<>())
                        .add(new QuestionEvidence(question.id(), evidence))));
        Map<String, Integer> ordinals = new LinkedHashMap<>();
        List<EvidenceCoverageHunk> result = new ArrayList<>();
        for (StagedHunk hunk : captured.hunks()) {
            String path = portable(hunk.file().path().toString());
            int ordinal = ordinals.merge(path, 1, Integer::sum);
            if (hunk.truncated()) {
                result.add(new EvidenceCoverageHunk(path, ordinal, anchor(hunk), anchor(hunk), false,
                        EvidenceCoverageState.UNMEASURABLE, List.of()));
                continue;
            }
            List<String> categories = evidenceByPath.getOrDefault(path, List.of()).stream()
                    .filter(value -> intersects(hunk, value.evidence()))
                    .map(QuestionEvidence::questionId).distinct().toList();
            int start = displayStart(hunk);
            int end = displayEnd(hunk, start);
            boolean navigable = hunk.file().status() != StagedFileStatus.DELETED;
            result.add(new EvidenceCoverageHunk(path, ordinal, start, end, navigable,
                    categories.isEmpty() ? EvidenceCoverageState.UNREFERENCED
                            : EvidenceCoverageState.REFERENCED, categories));
        }
        return new EvidenceCoverageMap(captured.change().diffFingerprint(), result);
    }

    private static boolean intersects(StagedHunk hunk, CodeEvidence evidence) {
        List<SourceLineRange> ranges = hunk.changedNewLineRanges();
        if (!ranges.isEmpty()) {
            return ranges.stream().anyMatch(range -> overlaps(range.startLine(), range.endLine(),
                    evidence.startLine(), evidence.endLine()));
        }
        if (hunk.oldLineCount() > 0) {
            return overlaps(hunk.oldStartLine(), hunk.oldStartLine() + hunk.oldLineCount() - 1,
                    evidence.startLine(), evidence.endLine());
        }
        return false;
    }

    private static int displayStart(StagedHunk hunk) {
        return hunk.changedNewLineRanges().isEmpty() ? anchor(hunk)
                : hunk.changedNewLineRanges().getFirst().startLine();
    }

    private static int displayEnd(StagedHunk hunk, int start) {
        return hunk.changedNewLineRanges().isEmpty() ? start
                : hunk.changedNewLineRanges().getLast().endLine();
    }

    private static int anchor(StagedHunk hunk) {
        return Math.max(1, hunk.newStartLine());
    }

    private static boolean overlaps(int leftStart, int leftEnd, int rightStart, int rightEnd) {
        return leftStart <= rightEnd && rightStart <= leftEnd;
    }

    private static String portable(String value) {
        return value.replace('\\', '/');
    }

    private record QuestionEvidence(String questionId, CodeEvidence evidence) { }
}
