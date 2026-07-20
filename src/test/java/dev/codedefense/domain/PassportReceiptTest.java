package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.passport.PassportTestFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PassportReceiptTest {
    @Test
    void createsSourceFreeReceiptFromPassport() {
        PassportReceipt receipt = PassportReceipt.from(
                PassportTestFixtures.passport(PassportStatus.CURRENT),
                "7bd53719-1de8-4c78-a48a-430aa38555dc");

        assertEquals(3, receipt.schemaVersion());
        assertEquals(DefenseFocus.BALANCED, receipt.focus());
        assertEquals(1, receipt.attemptNumber());
        assertEquals(ChangeKind.STAGED, receipt.changeKind());
        assertEquals("a".repeat(64), receipt.repositoryIdentityHash());
        assertEquals("c".repeat(64), receipt.sourceIdentity());
        assertEquals(List.of("decision", "counterfactual", "test-prediction"),
                receipt.categories().stream().map(PassportCategoryReceipt::id).toList());
        assertEquals("src/App.java", receipt.files().getFirst().path());
        assertEquals(55, receipt.overallScore());
        assertFalse(receipt.toString().contains("src/App.java"));
        assertFalse(receipt.toString().contains("private-answer"));
        assertFalse(receipt.toString().contains("feedback"));
    }

    @Test
    void copiesCollectionsAndRejectsUnsafePaths() {
        List<PassportFileReceipt> files = new ArrayList<>();
        files.add(new PassportFileReceipt("src/App.java", null, "MODIFIED", 2, 1));
        PassportReceipt receipt = receipt(files, categories());
        files.clear();

        assertEquals(1, receipt.files().size());
        assertThrows(UnsupportedOperationException.class,
                () -> receipt.files().add(new PassportFileReceipt("src/X.java", null, "ADDED", 1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> receipt(List.of(new PassportFileReceipt("../secret.txt", null, "MODIFIED", 1, 1)), categories()));
        assertThrows(IllegalArgumentException.class,
                () -> receipt(List.of(new PassportFileReceipt("C:/secret.txt", null, "MODIFIED", 1, 1)), categories()));
    }

    @Test
    void requiresThreeOrderedCategoriesAndConsistentFollowUpPair() {
        assertThrows(IllegalArgumentException.class,
                () -> receipt(files(), categories().subList(0, 2)));
        List<PassportCategoryReceipt> wrongOrder = new ArrayList<>(categories());
        java.util.Collections.swap(wrongOrder, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> receipt(files(), wrongOrder));
        assertThrows(IllegalArgumentException.class, () -> receipt(files(), List.of(
                new PassportCategoryReceipt("decision", Verdict.PARTIAL, 50,
                        Optional.of(Verdict.CORRECT), Optional.empty(), 50),
                categories().get(1), categories().get(2))));
    }

    @Test
    void validatesVersionIdentifiersHashesAndScores() {
        assertThrows(IllegalArgumentException.class,
                () -> new PassportReceipt(4, "7bd53719-1de8-4c78-a48a-430aa38555dc",
                        "a".repeat(64), ChangeKind.STAGED, "b".repeat(40), "c".repeat(64),
                        "d".repeat(64), java.time.Instant.EPOCH, PassportStatus.CURRENT, files(),
                        categories(), 55, Readiness.REVIEW_NEEDED, 0, "model"));
        assertThrows(IllegalArgumentException.class,
                () -> receipt("not-a-uuid", "a".repeat(64), 55));
        assertThrows(IllegalArgumentException.class,
                () -> receipt("7bd53719-1de8-4c78-a48a-430aa38555dc", "A".repeat(64), 55));
        assertThrows(IllegalArgumentException.class,
                () -> receipt("7bd53719-1de8-4c78-a48a-430aa38555dc", "a".repeat(64), 101));
    }

    @Test
    void validatesAttemptLineage() {
        PassportReceipt first = receipt(files(), categories());
        PassportAttemptId child = new PassportAttemptId("11111111-1111-4111-8111-111111111111");
        PassportReceipt second = new PassportReceipt(2, child.value(), first.repositoryIdentityHash(),
                first.changeKind(), first.baseCommit(), first.sourceIdentity(), first.diffFingerprint(),
                first.createdAt().plusSeconds(1), first.statusAtCreation(), first.files(), first.categories(),
                70, Readiness.STRONG_UNDERSTANDING, 0, "model", child, Optional.of(first.attemptId()), 2,
                DefenseFocus.TESTING);
        assertEquals(2, second.attemptSummary().attemptNumber());
        assertThrows(IllegalArgumentException.class, () -> new PassportReceipt(2, child.value(),
                first.repositoryIdentityHash(), first.changeKind(), first.baseCommit(), first.sourceIdentity(),
                first.diffFingerprint(), first.createdAt(), first.statusAtCreation(), first.files(),
                first.categories(), 70, Readiness.STRONG_UNDERSTANDING, 0, "model", child, Optional.empty(), 2,
                DefenseFocus.BALANCED));
    }

    private static PassportReceipt receipt(List<PassportFileReceipt> files,
            List<PassportCategoryReceipt> categories) {
        return new PassportReceipt(1, "7bd53719-1de8-4c78-a48a-430aa38555dc",
                "a".repeat(64), ChangeKind.STAGED, "b".repeat(40), "c".repeat(64),
                "d".repeat(64), java.time.Instant.EPOCH, PassportStatus.CURRENT,
                files, categories, 55, Readiness.REVIEW_NEEDED, 0, "model");
    }

    private static PassportReceipt receipt(String id, String repositoryHash, int score) {
        return new PassportReceipt(1, id, repositoryHash, ChangeKind.STAGED,
                "b".repeat(40), "c".repeat(64), "d".repeat(64), java.time.Instant.EPOCH,
                PassportStatus.CURRENT, files(), categories(), score,
                Readiness.REVIEW_NEEDED, 0, "model");
    }

    private static List<PassportFileReceipt> files() {
        return List.of(new PassportFileReceipt("src/App.java", null, "MODIFIED", 2, 1));
    }

    private static List<PassportCategoryReceipt> categories() {
        return List.of(
                new PassportCategoryReceipt("decision", Verdict.PARTIAL, 74,
                        Optional.empty(), Optional.empty(), 74),
                new PassportCategoryReceipt("counterfactual", Verdict.PARTIAL, 90,
                        Optional.empty(), Optional.empty(), 90),
                new PassportCategoryReceipt("test-prediction", Verdict.SKIPPED, 0,
                        Optional.empty(), Optional.empty(), 0));
    }
}
