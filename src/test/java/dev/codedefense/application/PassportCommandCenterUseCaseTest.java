package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportReceiptJsonCodec;
import dev.codedefense.passport.PassportTerminalRenderer;
import dev.codedefense.passport.PassportTestFixtures;
import dev.codedefense.passport.StoredChangePassport;
import dev.codedefense.passport.StoredPassportIdentity;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PassportCommandCenterUseCaseTest {
    @TempDir Path directory;

    @Test
    void showsCurrentReceiptAndThreeCategoryScores() {
        StoredChangePassport stored = stored(PassportStatus.CURRENT);
        ChangePassportStore store = store(stored);
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        var verifier = new VerifyLatestChangePassportUseCase(
                path -> new CapturedStagedChange(passport.change(), List.of()), store);
        var useCase = new ShowLatestChangePassportUseCase(store, verifier,
                new PassportTerminalRenderer());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, useCase.show(directory,
                new PrintWriter(bytes, true, StandardCharsets.UTF_8), new PrintWriter(System.err)));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Change Passport: CURRENT"));
        assertTrue(output.contains("decision: 74/100"));
        assertTrue(output.contains("counterfactual: 90/100"));
        assertTrue(output.contains("test-prediction: 0/100"));
        assertFalse(output.contains("private-answer"));
        assertFalse(output.contains("feedback"));
    }

    @Test
    void listsNewestFirstAndHandlesEmptyStorage() {
        StoredChangePassport current = stored(PassportStatus.CURRENT);
        StoredChangePassport expired = stored(PassportStatus.EXPIRED);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var useCase = new ListChangePassportsUseCase(store(List.of(expired, current)),
                new PassportTerminalRenderer());

        assertEquals(ExitCodes.SUCCESS, useCase.list(directory, 10,
                new PrintWriter(bytes, true, StandardCharsets.UTF_8), new PrintWriter(System.err)));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).indexOf("EXPIRED")
                < bytes.toString(StandardCharsets.UTF_8).indexOf("CURRENT"));

        bytes.reset();
        assertEquals(ExitCodes.SUCCESS, new ListChangePassportsUseCase(store(List.of()),
                new PassportTerminalRenderer()).list(directory, 10,
                new PrintWriter(bytes, true, StandardCharsets.UTF_8), new PrintWriter(System.err)));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("No Change Passports are available"));
    }

    @Test
    void exportsExactReceiptBytesWithoutOverwriting() throws Exception {
        StoredChangePassport stored = stored(PassportStatus.CURRENT);
        PassportReceiptJsonCodec codec = new PassportReceiptJsonCodec();
        ExportChangePassportUseCase useCase = new ExportChangePassportUseCase(store(stored), codec);
        Path output = directory.resolve("passport.json");

        assertEquals(ExitCodes.SUCCESS, useCase.export(directory, output,
                new PrintWriter(System.out), new PrintWriter(System.err)));
        assertArrayEquals(codec.encode(stored.receipt()), Files.readAllBytes(output));
        assertThrows(dev.codedefense.passport.ChangePassportPersistenceException.class,
                () -> useCase.export(directory, output, new PrintWriter(System.out),
                        new PrintWriter(System.err)));
    }

    private StoredChangePassport stored(PassportStatus status) {
        PassportReceipt receipt = PassportReceipt.from(PassportTestFixtures.passport(status),
                java.util.UUID.randomUUID().toString());
        Path base = directory.resolve(status.name().toLowerCase());
        return new StoredChangePassport(base.resolveSibling(base.getFileName() + ".md"),
                base.resolveSibling(base.getFileName() + ".json"), receipt);
    }

    private static ChangePassportStore store(StoredChangePassport stored) {
        return store(List.of(stored));
    }

    private static ChangePassportStore store(List<StoredChangePassport> values) {
        return new ChangePassportStore() {
            @Override public Path save(dev.codedefense.domain.ChangePassport passport) {
                throw new AssertionError("save must not be called");
            }
            @Override public Optional<StoredPassportIdentity> readLatestIdentity() {
                return values.isEmpty() ? Optional.empty() : Optional.of(StoredPassportIdentity.from(
                        PassportTestFixtures.passport(values.getFirst().receipt().statusAtCreation()),
                        values.getFirst().markdownPath()));
            }
            @Override public Optional<StoredChangePassport> readLatest() {
                return values.stream().findFirst();
            }
            @Override public List<StoredChangePassport> list(int limit) {
                return values.stream().limit(limit).toList();
            }
        };
    }
}
