package dev.codedefense.jetbrains.status;

import java.util.List;
import java.util.Objects;

public record PassportStatusView(boolean present, String status, String changeKind,
        String shortFingerprint, String focus, int attemptNumber, int overallScore,
        String readiness, List<CategoryScore> categories) {
    public PassportStatusView {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(changeKind, "changeKind");
        Objects.requireNonNull(shortFingerprint, "shortFingerprint");
        Objects.requireNonNull(focus, "focus");
        Objects.requireNonNull(readiness, "readiness");
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        if (attemptNumber < 0 || overallScore < 0 || overallScore > 100) {
            throw new IllegalArgumentException("Passport status values are invalid.");
        }
    }

    public static PassportStatusView absent() {
        return new PassportStatusView(false, "No Passport", "", "", "", 0, 0, "", List.of());
    }

    public record CategoryScore(String id, int score) {
        public CategoryScore {
            Objects.requireNonNull(id, "id");
            if (id.isBlank() || score < 0 || score > 100) {
                throw new IllegalArgumentException("Category score is invalid.");
            }
        }
    }
}
