package dev.codedefense.passport;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.report.MarkdownTextEscaper;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Renders a deliberately source-free, educational Change Passport. */
public final class MarkdownChangePassportRenderer {
    private static final String METADATA_PREFIX = "<!-- codedefense-change-passport:v1;";

    public String render(ChangePassport passport) {
        Objects.requireNonNull(passport, "passport");
        StringBuilder markdown = new StringBuilder();
        line(markdown, "# CodeDefense Change Passport"); line(markdown, "");
        line(markdown, "## Change identity");
        line(markdown, "- Base commit: " + passport.change().baseCommit());
        line(markdown, "- Index identity: " + passport.change().indexIdentity());
        line(markdown, "- Diff fingerprint: " + passport.change().diffFingerprint());
        line(markdown, "- Created at: " + DateTimeFormatter.ISO_INSTANT.format(passport.createdAt())); line(markdown, "");
        line(markdown, "## Status"); line(markdown, "- Status at creation: " + passport.statusAtCreation());
        appendProvenance(markdown, passport); line(markdown, "");
        line(markdown, "## Local assessment");
        line(markdown, "- Java-owned final score: " + passport.session().overallScore() + "/100");
        line(markdown, "- Java-owned readiness: " + MarkdownTextEscaper.inline(passport.session().readiness().displayName())); line(markdown, "");
        line(markdown, "## Changed files");
        for (StagedChangeFile file : passport.change().files()) {
            String path = file.previousPath()
                    .map(previous -> previous.toString().replace('\\', '/') + " → "
                            + file.path().toString().replace('\\', '/'))
                    .orElseGet(() -> file.path().toString().replace('\\', '/'));
            line(markdown, "- " + MarkdownTextEscaper.inline(path) + " — " + file.status() + " ("
                    + file.addedLines() + " added, " + file.deletedLines() + " deleted)");
        }
        line(markdown, "");
        for (QuestionResult result : passport.session().results()) {
            appendQuestion(markdown, result);
        }
        line(markdown, "## Privacy");
        line(markdown, "This educational artifact contains only bounded change metadata and structured local assessment results.");
        line(markdown, "It is not approval to merge or deploy and does not claim Codex authored the change."); line(markdown, "");
        line(markdown, metadata(passport));
        return markdown.toString();
    }
    private static void appendProvenance(StringBuilder markdown, ChangePassport passport) {
        if (passport.codexProvenance().isEmpty()) {
            line(markdown, "- Experimental Codex provenance: Not requested");
            return;
        }
        var provenance = passport.codexProvenance().orElseThrow();
        line(markdown, "- Experimental Codex provenance: " + switch (provenance.status()) {
            case EXACT_CHANGE_MATCH -> "Exact change match";
            case PARTIAL_PATH_MATCH -> "Partial path match";
            case NO_MATCH -> "No match";
            case UNAVAILABLE -> "Unavailable";
        });
        line(markdown, "- Matched files: " + provenance.matchedFileCount() + "/"
                + provenance.selectedFileCount());
        if (provenance.status() != dev.codedefense.domain.CodexProvenanceStatus.UNAVAILABLE) {
            line(markdown, "- Codex version: " + MarkdownTextEscaper.inline(provenance.codexVersion()));
            line(markdown, "- Selected thread identity hash: " + provenance.threadIdentityHash());
            for (String path : provenance.matchedRelativePaths()) {
                line(markdown, "  - " + MarkdownTextEscaper.inline(path));
            }
        }
        line(markdown, "- This is evidence consistency only; it does not prove authorship, causation, review quality, or safety.");
    }
    String metadata(ChangePassport passport) {
        StoredPassportIdentity identity = StoredPassportIdentity.from(passport, java.nio.file.Path.of("passport.md").toAbsolutePath());
        return METADATA_PREFIX + "root=" + identity.repositoryIdentityHash() + ";base=" + identity.baseCommit() + ";index=" + identity.indexIdentity()
                + ";diff=" + identity.diffFingerprint() + ";paths=" + String.join(",", identity.changedPathHashes())
                + ";timestamp=" + DateTimeFormatter.ISO_INSTANT.format(identity.createdAt()) + " -->";
    }
    private static void appendQuestion(StringBuilder markdown, QuestionResult result) {
        line(markdown, "## " + headingFor(result.question().id()));
        line(markdown, "- Evidence: " + evidence(result.question().evidence()));
        appendEvaluation(markdown, "### Primary evaluation", result.primaryTurn());
        result.followUpTurn().ifPresent(turn -> appendEvaluation(markdown, "### Follow-up evaluation", turn));
        line(markdown, "- Local final score: " + result.finalScore() + "/100");
        line(markdown, "");
    }

    private static void appendEvaluation(StringBuilder markdown, String heading,
            dev.codedefense.domain.InterviewTurn turn) {
        line(markdown, heading);
        line(markdown, "- Verdict: " + MarkdownTextEscaper.inline(turn.evaluation().verdict().name()));
        line(markdown, "- Score: " + turn.evaluation().score() + "/100");
    }
    private static String headingFor(String questionId) {
        return switch (questionId) {
            case "decision" -> "Decision defense";
            case "counterfactual" -> "Counterfactual defense";
            case "test-prediction" -> "Test prediction";
            default -> throw new IllegalArgumentException("Unknown staged-change question category");
        };
    }
    private static String evidence(java.util.List<CodeEvidence> evidence) {
        return evidence.stream()
                .map(item -> MarkdownTextEscaper.inline(item.path()) + ":" + item.startLine() + "-" + item.endLine())
                .collect(java.util.stream.Collectors.joining(", "));
    }
    private static void line(StringBuilder builder, String value) { builder.append(value).append('\n'); }
}
