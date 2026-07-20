package dev.codedefense.ci;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.Objects;

public final class PassportContinuityRenderer {
    public enum Format {
        TEXT, JSON, GITHUB;

        public static Format parse(String value) {
            return valueOf(Objects.requireNonNull(value, "value").toUpperCase(Locale.ROOT));
        }
    }

    public void render(PassportContinuityResult result, CiPassportPolicy policy, Format format, PrintWriter out) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(out, "out");
        switch (format) {
            case TEXT -> text(result, policy, out);
            case JSON -> json(result, policy, out);
            case GITHUB -> github(result, policy, out);
        }
    }

    private void text(PassportContinuityResult result, CiPassportPolicy policy, PrintWriter out) {
        out.println("CodeDefense Passport continuity");
        out.println("Policy: " + policy.name().toLowerCase(Locale.ROOT));
        for (CommitContinuityResult commit : result.commits()) {
            out.println(shortId(commit.commitId()) + "  " + commit.status());
        }
        summary(result, out);
    }

    private void json(PassportContinuityResult result, CiPassportPolicy policy, PrintWriter out) {
        String commits = result.commits().stream()
                .map(commit -> "{\"commit\":\"%s\",\"status\":\"%s\"}"
                        .formatted(shortId(commit.commitId()), commit.status()))
                .collect(java.util.stream.Collectors.joining(","));
        out.println("{\"schemaVersion\":1,\"policy\":\"%s\",\"allMatched\":%s,\"commits\":[%s],"
                .formatted(policy.name().toLowerCase(Locale.ROOT), result.allMatched(), commits)
                + "\"sourceFree\":true,\"codexInvoked\":false}");
    }

    private void github(PassportContinuityResult result, CiPassportPolicy policy, PrintWriter out) {
        out.println("## CodeDefense Passport continuity");
        out.println();
        out.println("| Commit | Status |");
        out.println("|---|---|");
        for (CommitContinuityResult commit : result.commits()) {
            out.println("| `" + shortId(commit.commitId()) + "` | **" + commit.status() + "** |");
        }
        out.println();
        out.println("Policy: **" + policy.name().toLowerCase(Locale.ROOT) + "**");
        summary(result, out);
    }

    private void summary(PassportContinuityResult result, PrintWriter out) {
        out.println("Matched: " + result.count(CiPassportStatus.MATCHED)
                + ", missing: " + result.count(CiPassportStatus.MISSING)
                + ", mismatch: " + result.count(CiPassportStatus.MISMATCH)
                + ", unavailable: " + result.count(CiPassportStatus.UNAVAILABLE));
        out.println("Fingerprint continuity only: not identity, correctness, safety, merge approval, or deployment approval.");
        out.println("Source-free check. Codex was not invoked.");
    }

    private static String shortId(String value) {
        return value.substring(0, Math.min(12, value.length()));
    }
}
