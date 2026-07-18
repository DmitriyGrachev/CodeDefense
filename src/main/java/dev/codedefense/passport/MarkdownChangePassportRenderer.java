package dev.codedefense.passport;

import dev.codedefense.domain.ChangePassport;
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
        line(markdown, "- Index tree: " + passport.change().indexTree());
        line(markdown, "- Diff fingerprint: " + passport.change().diffFingerprint());
        line(markdown, "- Created at: " + DateTimeFormatter.ISO_INSTANT.format(passport.createdAt())); line(markdown, "");
        line(markdown, "## Status"); line(markdown, "- Status at creation: " + passport.statusAtCreation());
        line(markdown, "- Codex session link: NOT_REQUESTED"); line(markdown, "");
        line(markdown, "## Local assessment");
        line(markdown, "- Java-owned final score: " + passport.session().overallScore() + "/100");
        line(markdown, "- Java-owned readiness: " + MarkdownTextEscaper.inline(passport.session().readiness().displayName())); line(markdown, "");
        line(markdown, "## Changed files");
        for (StagedChangeFile file : passport.change().files()) line(markdown, "- " + MarkdownTextEscaper.inline(file.path().toString().replace('\\', '/')) + " — " + file.status() + " (" + file.addedLines() + " added, " + file.deletedLines() + " deleted)");
        line(markdown, "");
        appendQuestion(markdown, "Decision defense", passport.session().results().get(0));
        appendQuestion(markdown, "Counterfactual defense", passport.session().results().get(1));
        appendQuestion(markdown, "Test prediction", passport.session().results().get(2));
        line(markdown, "## Privacy");
        line(markdown, "This source-free educational artifact is not approval to merge or deploy, and does not claim Codex authored a change.");
        line(markdown, "It omits staged source, diffs, blobs, prompts, raw model JSON, user answers, expected key points, and evidence reasons."); line(markdown, "");
        line(markdown, metadata(passport));
        return markdown.toString();
    }
    String metadata(ChangePassport passport) {
        StoredPassportIdentity identity = StoredPassportIdentity.from(passport, java.nio.file.Path.of("passport.md").toAbsolutePath());
        return METADATA_PREFIX + "root=" + identity.repositoryIdentityHash() + ";base=" + identity.baseCommit() + ";tree=" + identity.indexTree()
                + ";diff=" + identity.diffFingerprint() + ";paths=" + String.join(",", identity.changedPathHashes())
                + ";timestamp=" + DateTimeFormatter.ISO_INSTANT.format(identity.createdAt()) + " -->";
    }
    private static void appendQuestion(StringBuilder markdown, String heading, QuestionResult result) {
        line(markdown, "## " + heading);
        line(markdown, "- Local final score: " + result.finalScore() + "/100");
        line(markdown, "- Verdict: " + result.primaryTurn().evaluation().verdict());
        line(markdown, "- Feedback: " + MarkdownTextEscaper.inline(result.primaryTurn().evaluation().feedback()));
        line(markdown, "");
    }
    private static void line(StringBuilder builder, String value) { builder.append(value).append('\n'); }
}
