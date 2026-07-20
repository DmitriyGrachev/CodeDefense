package dev.codedefense.cli;

import dev.codedefense.CodeDefenseApplication;
import dev.codedefense.application.EvaluateStagedPassportGateUseCase;
import dev.codedefense.change.GitChangeException;
import dev.codedefense.change.StagedChangeSource;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.StagedPassportGateJsonCodec;
import dev.codedefense.passport.StoredChangePassport;
import dev.codedefense.passport.StoredPassportIdentity;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassportGateCommandTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void stagedAndJsonFormatAreRequired() {
        CommandLine commandLine = commandLine(useCase(path -> change(), emptyStore()), new ByteArrayOutputStream(),
                new ByteArrayOutputStream());

        assertEquals(ExitCodes.INVALID_USAGE, commandLine.execute("--format", "json"));
        assertEquals(ExitCodes.INVALID_USAGE, commandLine.execute("--staged"));
    }

    @Test
    void rejectsNonJsonFormatWithoutLeakingTheRawInput() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(useCase(path -> change(), emptyStore()), new ByteArrayOutputStream(), error);

        assertEquals(ExitCodes.INVALID_USAGE, commandLine.execute("--staged", "--format", "yaml"));

        String message = error.toString(StandardCharsets.UTF_8);
        assertTrue(message.contains("Only --format json is supported."));
        assertFalse(message.contains("yaml"));
    }

    @Test
    void defaultsPathToCurrentDirectoryAndWritesBoundedDeterministicUtf8Json() {
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = commandLine(useCase(path -> {
            calls.incrementAndGet();
            assertEquals(Path.of(".").toAbsolutePath().normalize(), path.toAbsolutePath().normalize());
            return change();
        }, emptyStore()), output, new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--staged", "--format", "json"));

        byte[] bytes = output.toByteArray();
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(1, calls.get());
        assertTrue(json.endsWith("\n"));
        assertTrue(json.contains("\"state\":\"UNDEFENDED\""));
        assertTrue(bytes.length <= 256 * 1024);
    }

    @Test
    void everyOperationalGateStateReturnsSuccess() {
        assertEquals(ExitCodes.SUCCESS, execute(useCase(path -> { throw new GitChangeException(
                GitChangeException.Kind.NO_STAGED_CHANGE); }, emptyStore())));
        assertEquals(ExitCodes.SUCCESS, execute(useCase(path -> change(), emptyStore())));
        assertEquals(ExitCodes.SUCCESS, execute(useCase(path -> { throw new GitChangeException(
                GitChangeException.Kind.INVALID_REPOSITORY); }, emptyStore())));
    }

    @Test
    void helpAndVersionDoNotEvaluateTheGate() {
        AtomicInteger calls = new AtomicInteger();
        CommandLine commandLine = commandLine(useCase(path -> {
            calls.incrementAndGet();
            throw new AssertionError("gate evaluation must stay lazy");
        }, emptyStore()), new ByteArrayOutputStream(), new ByteArrayOutputStream());

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--help"));
        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--version"));
        assertEquals(0, calls.get());
    }

    @Test
    void productionInvalidUsageNeverEchoesPrivateGateArgumentsOrStackTraces() {
        String privateOption = "--private-option-marker=C:\\private-path-marker";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandLine commandLine = CodeDefenseApplication.createCommandLine();
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));

        assertEquals(ExitCodes.INVALID_USAGE,
                commandLine.execute("passport", "gate", "--staged", "--format", "json", privateOption));

        String text = output.toString(StandardCharsets.UTF_8) + error.toString(StandardCharsets.UTF_8);
        assertFalse(text.contains("private-option-marker"));
        assertFalse(text.contains("private-path-marker"));
        assertFalse(text.contains("picocli.CommandLine"));
        assertFalse(text.contains("\tat "));
    }

    private static int execute(EvaluateStagedPassportGateUseCase useCase) {
        return commandLine(useCase, new ByteArrayOutputStream(), new ByteArrayOutputStream())
                .execute("--staged", "--format", "json");
    }

    private static CommandLine commandLine(EvaluateStagedPassportGateUseCase useCase,
            ByteArrayOutputStream output, ByteArrayOutputStream error) {
        CommandLine commandLine = new CommandLine(new PassportGateCommand(useCase, new StagedPassportGateJsonCodec()));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(error, true, StandardCharsets.UTF_8));
        commandLine.setParameterExceptionHandler((exception, arguments) -> ExitCodes.INVALID_USAGE);
        return commandLine;
    }

    private static EvaluateStagedPassportGateUseCase useCase(
            java.util.function.Function<Path, StagedChange> source, ChangePassportStore store) {
        return new EvaluateStagedPassportGateUseCase(source(source), store);
    }

    private static StagedChangeSource source(java.util.function.Function<Path, StagedChange> inspections) {
        return new StagedChangeSource() {
            @Override public dev.codedefense.change.CapturedStagedChange capture(Path path) {
                throw new AssertionError("gate evaluation must not capture source content");
            }
            @Override public StagedChange inspect(Path path) { return inspections.apply(path); }
        };
    }

    private static StagedChange change() {
        return new StagedChange(Path.of(".").toAbsolutePath().normalize(), HASH, "b".repeat(40), "c".repeat(64), HASH,
                List.of(new StagedChangeFile(Path.of("src/App.java"), Optional.empty(), StagedFileStatus.MODIFIED, 3, 1)),
                3, 1);
    }

    private static ChangePassportStore emptyStore() {
        return new ChangePassportStore() {
            @Override public Path save(dev.codedefense.domain.ChangePassport passport) { throw new AssertionError(); }
            @Override public Optional<StoredPassportIdentity> readLatestIdentity() { return Optional.empty(); }
            @Override public List<StoredChangePassport> listByRepositoryAndKind(String identity, ChangeKind kind, int limit) {
                return List.of();
            }
        };
    }
}
