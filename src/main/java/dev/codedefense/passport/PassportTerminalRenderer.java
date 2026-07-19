package dev.codedefense.passport;

import dev.codedefense.domain.PassportCategoryReceipt;
import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.terminal.TerminalTextSanitizer;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

/** Plain-text rendering of source-free receipt facts. */
public final class PassportTerminalRenderer {
    public void render(PassportReceipt receipt, PassportStatus currentStatus, PrintWriter out) {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(out, "out");
        out.println("Change Passport: " + currentStatus);
        out.println("Change: " + receipt.changeKind());
        out.println("Fingerprint: " + receipt.diffFingerprint().substring(0, 12));
        out.println("Created: " + receipt.createdAt());
        out.println("Category scores");
        for (PassportCategoryReceipt category : receipt.categories()) {
            out.println("- " + TerminalTextSanitizer.singleLine(category.id()) + ": "
                    + category.finalScore() + "/100");
        }
        out.println("Overall score: " + receipt.overallScore() + "/100");
        out.println("Readiness: " + TerminalTextSanitizer.singleLine(receipt.readiness().displayName()));
        out.println("Educational signal only. This is not approval to merge or deploy.");
    }

    public void renderList(List<StoredChangePassport> values, PrintWriter out) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(out, "out");
        if (values.isEmpty()) {
            out.println("No Change Passports are available yet.");
            return;
        }
        for (StoredChangePassport stored : values) {
            PassportReceipt receipt = stored.receipt();
            out.println(receipt.createdAt() + "  " + receipt.statusAtCreation() + "  "
                    + receipt.diffFingerprint().substring(0, 12) + "  "
                    + receipt.overallScore() + "/100  " + receipt.receiptId());
        }
    }

    public void renderTimeline(java.util.List<StoredChangePassport> attempts, java.io.PrintWriter out) {
        Objects.requireNonNull(attempts); Objects.requireNonNull(out);
        out.println("Change Passport timeline");
        Integer previous = null;
        java.util.Map<String, Integer> previousCategories = new java.util.HashMap<>();
        for (StoredChangePassport stored : attempts.reversed()) {
            var receipt = stored.receipt();
            out.println("Attempt " + receipt.attemptNumber() + " - " + receipt.createdAt());
            out.println("Overall score: " + receipt.overallScore() + "/100"
                    + (previous == null ? "" : " (" + signed(receipt.overallScore() - previous) + ")"));
            out.println("Readiness: " + receipt.readiness().displayName());
            for (var category : receipt.categories()) {
                Integer prior = previousCategories.put(category.id(), category.finalScore());
                out.println("- " + category.id() + ": " + category.finalScore() + "/100"
                        + (prior == null ? "" : " (" + signed(category.finalScore() - prior) + ")"));
            }
            previous = receipt.overallScore();
        }
        out.flush();
    }

    private static String signed(int value) { return value >= 0 ? "+" + value : Integer.toString(value); }
}
