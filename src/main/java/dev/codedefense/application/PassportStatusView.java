package dev.codedefense.application;

import dev.codedefense.domain.PassportCategoryReceipt;
import dev.codedefense.domain.PassportStatus;
import java.util.List;
import java.util.Objects;

public record PassportStatusView(int protocolVersion, boolean present, PassportStatus status,
        String changeKind, String shortFingerprint, String focus, int attemptNumber,
        int overallScore, String readiness, List<PassportCategoryReceipt> categories) {
    public PassportStatusView {
        if (protocolVersion != 1) throw new IllegalArgumentException("protocolVersion must be 1");
        Objects.requireNonNull(status); Objects.requireNonNull(changeKind); Objects.requireNonNull(shortFingerprint);
        Objects.requireNonNull(focus); Objects.requireNonNull(readiness); categories = List.copyOf(categories);
        if (attemptNumber < 0 || overallScore < 0 || overallScore > 100) throw new IllegalArgumentException("invalid status values");
    }
}
