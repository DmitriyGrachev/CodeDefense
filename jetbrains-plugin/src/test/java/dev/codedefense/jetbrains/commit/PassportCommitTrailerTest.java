package dev.codedefense.jetbrains.commit;

import static dev.codedefense.jetbrains.commit.PassportTrailerResult.Status.ADDED;
import static dev.codedefense.jetbrains.commit.PassportTrailerResult.Status.ALREADY_PRESENT;
import static dev.codedefense.jetbrains.commit.PassportTrailerResult.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PassportCommitTrailerTest {
    private static final String FINGERPRINT = "0123456789abcdef".repeat(4);
    private static final String OTHER_FINGERPRINT = "fedcba9876543210".repeat(4);
    private static final String TRAILER = "CodeDefense-Passport: sha256:" + FINGERPRINT;
    private final PassportCommitTrailer subject = new PassportCommitTrailer();

    @Test
    void addsASeparatedTrailerToAPlainSubject() {
        PassportTrailerResult result = subject.apply("Explain the decision", FINGERPRINT);

        assertEquals(ADDED, result.status());
        assertEquals("Explain the decision\n\n" + TRAILER, result.commitMessage());
        assertContainsIdentityOnly(result.commitMessage());
    }

    @Test
    void preservesSubjectBodyAndBlankLines() {
        String original = "Subject\n\nBody paragraph one.\n\nBody paragraph two.";

        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(ADDED, result.status());
        assertEquals(original + "\n\n" + TRAILER, result.commitMessage());
    }

    @Test
    void joinsAnExistingUnrelatedTrailerBlockWithoutChangingIt() {
        String original = "Subject\n\nBody\n\nSigned-off-by: Dev <dev@example.test>\nReviewed-by: Reviewer";

        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(ADDED, result.status());
        assertEquals(original + "\n" + TRAILER, result.commitMessage());
    }

    @Test
    void sameTrailerIsIdempotent() {
        String original = "Subject\n\n" + TRAILER;

        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(ALREADY_PRESENT, result.status());
        assertEquals(original, result.commitMessage());
    }

    @Test
    void aDifferentTrailerConflictsWithoutMutation() {
        String original = "Subject\n\nCodeDefense-Passport: sha256:" + OTHER_FINGERPRINT;

        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(CONFLICT, result.status());
        assertEquals(original, result.commitMessage());
    }

    @Test
    void duplicateCodeDefenseTrailersConflictEvenWhenBothMatch() {
        String original = "Subject\n\n" + TRAILER + "\n" + TRAILER;

        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(CONFLICT, result.status());
        assertEquals(original, result.commitMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "abc",
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
            "g123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0"
    })
    void rejectsMalformedFingerprint(String fingerprint) {
        assertThrows(IllegalArgumentException.class, () -> subject.apply("Subject", fingerprint));
    }

    @Test
    void rejectsNullInputs() {
        assertThrows(NullPointerException.class, () -> subject.apply(null, FINGERPRINT));
        assertThrows(NullPointerException.class, () -> subject.apply("Subject", null));
    }

    @ParameterizedTest
    @MethodSource("conflictingTrailerLines")
    void mixedCaseAndMalformedExistingTrailersConflictWithoutMutation(String existingLine) {
        String original = "Subject\n\n" + existingLine;

        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(CONFLICT, result.status());
        assertEquals(original, result.commitMessage());
    }

    static Stream<String> conflictingTrailerLines() {
        return Stream.of(
                "codedefense-passport: sha256:" + FINGERPRINT,
                "CODEDEFENSE-PASSPORT: sha256:" + FINGERPRINT,
                "CodeDefense-Passport: SHA256:" + FINGERPRINT,
                "CodeDefense-Passport: sha256:" + FINGERPRINT.toUpperCase(),
                "CodeDefense-Passport: " + FINGERPRINT,
                "CodeDefense-Passport:",
                "CodeDefense-Passport: sha256:" + FINGERPRINT + " extra");
    }

    @ParameterizedTest
    @MethodSource("lineEndingCases")
    void preservesEveryOriginalCharacterAndUsesTheFirstLineEnding(
            String original, String expectedSuffix) {
        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(ADDED, result.status());
        assertEquals(original + expectedSuffix + TRAILER, result.commitMessage());
        assertEquals(original, result.commitMessage().substring(0, original.length()));
    }

    static Stream<Arguments> lineEndingCases() {
        return Stream.of(
                Arguments.of("Subject\r\nBody", "\r\n\r\n"),
                Arguments.of("Subject\nBody", "\n\n"),
                Arguments.of("Subject\rBody", "\r\r"),
                Arguments.of("Subject\r\nBody\nStill body\rLast body", "\r\n\r\n"),
                Arguments.of("Subject", "\n\n"),
                Arguments.of("Subject\r\n", "\r\n"),
                Arguments.of("Subject\n\n", ""));
    }

    @Test
    void onlyCompleteLinesBeginningWithTheExactKeyAreClassified() {
        String original = "Subject\n\nExample: " + TRAILER + "\n CodeDefense-Passport: invalid";

        PassportTrailerResult result = subject.apply(original, FINGERPRINT);

        assertEquals(ADDED, result.status());
        assertEquals(original + "\n\n" + TRAILER, result.commitMessage());
    }

    private static void assertContainsIdentityOnly(String message) {
        assertEquals(1, message.split("\\Q" + TRAILER + "\\E", -1).length - 1);
        assertFalse(message.toLowerCase().contains("score"));
        assertFalse(message.toLowerCase().contains("readiness"));
        assertFalse(message.toLowerCase().contains("path"));
    }
}
