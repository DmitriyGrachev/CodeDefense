package dev.codedefense.cli;

import dev.codedefense.application.VerifyLatestChangePassportUseCase;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.PassportVerification;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportTestFixtures;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportCommandTest {
    @Test
    void verifyPrintsCurrentStatusAndExplanation() {
        AtomicInteger calls = new AtomicInteger();
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        VerifyLatestChangePassportUseCase useCase = new VerifyLatestChangePassportUseCase(path -> {
            calls.incrementAndGet();
            return new dev.codedefense.change.CapturedStagedChange(passport.change(), List.of(), "");
        }, storeWith(passport));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new PassportCommand(useCase));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--verify", "."));
        assertEquals(1, calls.get());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Change Passport: CURRENT"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("staged Git index"));
    }

    @Test
    void verifyOptionIsRequired() {
        assertEquals(ExitCodes.INVALID_USAGE, new CommandLine(new PassportCommand(new VerifyLatestChangePassportUseCase(
                path -> { throw new AssertionError(); }, emptyStore()))).execute());
    }

    private static ChangePassportStore emptyStore() {
        return new ChangePassportStore() {
            @Override public Path save(dev.codedefense.domain.ChangePassport passport) { throw new AssertionError(); }
            @Override public Optional<dev.codedefense.passport.StoredPassportIdentity> readLatestIdentity() { return Optional.empty(); }
        };
    }

    private static ChangePassportStore storeWith(dev.codedefense.domain.ChangePassport passport) {
        return new ChangePassportStore() {
            @Override public Path save(dev.codedefense.domain.ChangePassport value) { throw new AssertionError(); }
            @Override public Optional<dev.codedefense.passport.StoredPassportIdentity> readLatestIdentity() {
                return Optional.of(dev.codedefense.passport.StoredPassportIdentity.from(passport, Path.of("passport.md")));
            }
        };
    }
}
