package dev.codedefense.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

public record PassportReceipt(
        int schemaVersion,
        String receiptId,
        String repositoryIdentityHash,
        ChangeKind changeKind,
        String baseCommit,
        String sourceIdentity,
        String diffFingerprint,
        Instant createdAt,
        PassportStatus statusAtCreation,
        List<PassportFileReceipt> files,
        List<PassportCategoryReceipt> categories,
        int overallScore,
        Readiness readiness,
        int skippedPrimaryCount,
        String model,
        PassportAttemptId attemptId,
        Optional<PassportAttemptId> supersedes,
        int attemptNumber,
        DefenseFocus focus) {
    private static final List<String> CATEGORY_IDS =
            List.of("decision", "counterfactual", "test-prediction");

    public PassportReceipt {
        if (schemaVersion < 1 || schemaVersion > 3) {
            throw new IllegalArgumentException("unsupported receipt schema version");
        }
        receiptId = requireUuid(receiptId);
        repositoryIdentityHash = requireHash(repositoryIdentityHash, 64, 64, "repositoryIdentityHash");
        Objects.requireNonNull(changeKind, "changeKind");
        baseCommit = requireHash(baseCommit, 40, 64, "baseCommit");
        sourceIdentity = changeKind == ChangeKind.STAGED
                ? requireHash(sourceIdentity, 64, 64, "sourceIdentity")
                : requireHash(sourceIdentity, 40, 64, "sourceIdentity");
        diffFingerprint = requireHash(diffFingerprint, 64, 64, "diffFingerprint");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(statusAtCreation, "statusAtCreation");
        files = copyFiles(files);
        categories = copyCategories(categories);
        requireScore(overallScore, "overallScore");
        Objects.requireNonNull(readiness, "readiness");
        if (skippedPrimaryCount < 0 || skippedPrimaryCount > 3) {
            throw new IllegalArgumentException("skippedPrimaryCount must be between 0 and 3");
        }
        model = requireNonBlank(model, "model");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(supersedes, "supersedes");
        if (attemptNumber < 1 || attemptNumber == 1 && supersedes.isPresent()
                || attemptNumber > 1 && supersedes.isEmpty()
                || supersedes.filter(attemptId::equals).isPresent()) {
            throw new IllegalArgumentException("attempt lineage is invalid");
        }
        Objects.requireNonNull(focus, "focus");
    }

    public PassportReceipt(int schemaVersion, String receiptId, String repositoryIdentityHash,
            ChangeKind changeKind, String baseCommit, String sourceIdentity, String diffFingerprint,
            Instant createdAt, PassportStatus statusAtCreation, List<PassportFileReceipt> files,
            List<PassportCategoryReceipt> categories, int overallScore, Readiness readiness,
            int skippedPrimaryCount, String model) {
        this(schemaVersion, receiptId, repositoryIdentityHash, changeKind, baseCommit, sourceIdentity,
                diffFingerprint, createdAt, statusAtCreation, files, categories, overallScore, readiness,
                skippedPrimaryCount, model, new PassportAttemptId(receiptId), Optional.empty(), 1,
                DefenseFocus.BALANCED);
    }

    public static PassportReceipt from(ChangePassport passport, String receiptId) {
        Objects.requireNonNull(passport, "passport");
        return from(passport, receiptId, passport.focus());
    }

    public static PassportReceipt from(ChangePassport passport, String receiptId, DefenseFocus focus) {
        Objects.requireNonNull(passport, "passport");
        return new PassportReceipt(3, receiptId, passport.change().repositoryIdentityHash(),
                passport.changeKind(), passport.change().baseCommit(), passport.sourceIdentity(),
                passport.change().diffFingerprint(), passport.createdAt(), passport.statusAtCreation(),
                passport.change().files().stream().limit(30).map(PassportFileReceipt::from).toList(),
                passport.session().results().stream().map(PassportCategoryReceipt::from).toList(),
                passport.session().overallScore(), passport.session().readiness(),
                passport.session().skippedQuestionCount(), passport.model(),
                new PassportAttemptId(receiptId), Optional.empty(), 1, focus);
    }

    public PassportAttemptSummary attemptSummary() {
        return new PassportAttemptSummary(attemptId, supersedes, attemptNumber, diffFingerprint,
                createdAt, overallScore, readiness, categories);
    }

    private static List<PassportFileReceipt> copyFiles(List<PassportFileReceipt> values) {
        Objects.requireNonNull(values, "files");
        List<PassportFileReceipt> copy = List.copyOf(values);
        if (copy.isEmpty() || copy.size() > 30 || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("files must contain between 1 and 30 entries");
        }
        Set<String> paths = new HashSet<>();
        for (PassportFileReceipt file : copy) {
            if (!paths.add(file.path())) {
                throw new IllegalArgumentException("file paths must be unique");
            }
        }
        return copy;
    }

    private static List<PassportCategoryReceipt> copyCategories(List<PassportCategoryReceipt> values) {
        Objects.requireNonNull(values, "categories");
        List<PassportCategoryReceipt> copy = List.copyOf(values);
        if (copy.size() != CATEGORY_IDS.size()) {
            throw new IllegalArgumentException("exactly three categories are required");
        }
        for (int index = 0; index < CATEGORY_IDS.size(); index++) {
            if (!CATEGORY_IDS.get(index).equals(copy.get(index).id())) {
                throw new IllegalArgumentException("categories must use the required order");
            }
        }
        return copy;
    }

    private static String requireUuid(String value) {
        value = requireNonBlank(value, "receiptId");
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException("receiptId must use canonical UUID format");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("receiptId must use canonical UUID format", exception);
        }
    }

    private static String requireHash(String value, int minimum, int maximum, String field) {
        value = requireNonBlank(value, field);
        if (value.length() < minimum || value.length() > maximum || !value.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException(field + " has an invalid format");
        }
        return value;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank");
        }
        return value.strip();
    }

    private static void requireScore(int score, String field) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }

    @Override
    public String toString() {
        return ("PassportReceipt[schemaVersion=%d, receiptId=%s, changeKind=%s, fileCount=%d, "
                + "categoryCount=%d, overallScore=%d, readiness=%s, statusAtCreation=%s]")
                .formatted(schemaVersion, receiptId, changeKind, files.size(), categories.size(),
                        overallScore, readiness, statusAtCreation);
    }
}
