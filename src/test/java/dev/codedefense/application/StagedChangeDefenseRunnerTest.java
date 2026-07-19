package dev.codedefense.application;

import dev.codedefense.change.CapturedStagedChange;
import dev.codedefense.change.StagedHunk;
import dev.codedefense.change.StagedChangeContextBuilder;
import dev.codedefense.change.StagedChangePreviewRenderer;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.passport.PassportTestFixtures;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagedChangeDefenseRunnerTest {
    @Test
    void dryRunRendersPreviewWithoutConstructingInteractiveDependencies() {
        AtomicInteger confirmationConstructions = new AtomicInteger();
        AtomicInteger runtimeCalls = new AtomicInteger();
        AtomicInteger passportCalls = new AtomicInteger();
        DefaultStagedChangeDefenseRunner runner = new DefaultStagedChangeDefenseRunner(
                path -> captured(path), new StagedChangeContextBuilder(), new StagedChangePreviewRenderer(),
                () -> { confirmationConstructions.incrementAndGet(); return prompt -> false; },
                out -> { runtimeCalls.incrementAndGet(); throw new AssertionError("runtime must stay lazy"); },
                () -> { passportCalls.incrementAndGet(); throw new AssertionError("passport must stay lazy"); });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, runner.run(Path.of("."), true, false,
                new PrintWriter(bytes, true, StandardCharsets.UTF_8), new PrintWriter(System.err)));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Mode: Staged change"));
        assertTrue(output.contains("Unstaged working-tree content ignored: yes"));
        assertTrue(output.contains("No source content was sent."));
        assertEquals(0, confirmationConstructions.get());
        assertEquals(0, runtimeCalls.get());
        assertEquals(0, passportCalls.get());
    }

    @Test
    void declinePrintsFixedMessageWithoutConstructingRuntimeOrPassport() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        AtomicInteger passportCalls = new AtomicInteger();
        AtomicInteger confirmationConstructions = new AtomicInteger();
        DefaultStagedChangeDefenseRunner runner = new DefaultStagedChangeDefenseRunner(
                this::captured, new StagedChangeContextBuilder(), new StagedChangePreviewRenderer(),
                () -> { confirmationConstructions.incrementAndGet(); return prompt -> false; },
                out -> { runtimeCalls.incrementAndGet(); throw new AssertionError("runtime must stay lazy"); },
                () -> { passportCalls.incrementAndGet(); throw new AssertionError("passport must stay lazy"); });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, runner.run(Path.of("."), false, false,
                new PrintWriter(bytes, true, StandardCharsets.UTF_8), new PrintWriter(System.err)));

        assertTrue(bytes.toString(StandardCharsets.UTF_8)
                .contains("Cancelled before any source content was sent."));
        assertEquals(0, runtimeCalls.get());
        assertEquals(0, passportCalls.get());
        assertEquals(1, confirmationConstructions.get());
    }

    @Test
    void confirmedRunUsesStagedAnalyzerInterviewAndPreSaveRecaptureInOrder() {
        List<String> events = new ArrayList<>();
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        var source = (dev.codedefense.change.StagedChangeSource) path -> {
            events.add("capture");
            return captured(path);
        };
        dev.codedefense.passport.ChangePassportStore store = new dev.codedefense.passport.ChangePassportStore() {
            @Override public Path save(dev.codedefense.domain.ChangePassport value) {
                events.add("save"); return Path.of("passport.md").toAbsolutePath();
            }
            @Override public Optional<dev.codedefense.passport.StoredPassportIdentity> readLatestIdentity() {
                return Optional.empty();
            }
        };
        CodeDefenseRuntime runtime = new CodeDefenseRuntime(
                snapshot -> { throw new AssertionError("project analyzer must not run"); },
                () -> (change, snapshot) -> { events.add("analyze"); return passport.analysis(); },
                analysis -> { events.add("interview"); return passport.session(); },
                (snapshot, analysis, session) -> { throw new AssertionError("ordinary report must not run"); });
        DefaultStagedChangeDefenseRunner runner = new DefaultStagedChangeDefenseRunner(
                source, new StagedChangeContextBuilder(), new StagedChangePreviewRenderer(),
                () -> prompt -> { events.add("confirm"); return true; },
                out -> { events.add("runtime"); return runtime; },
                () -> { events.add("passport"); return new ChangePassportService(source, store, Clock.systemUTC(), "model"); });

        assertEquals(ExitCodes.SUCCESS, runner.run(Path.of("."), false, false,
                new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), new PrintWriter(System.err)));

        assertEquals(List.of("capture", "confirm", "runtime", "analyze", "interview", "passport", "capture", "save"), events);
    }

    @Test
    void mapsGitFailuresToSafeDocumentedExitCodes() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        AtomicInteger confirmationConstructions = new AtomicInteger();
        DefaultStagedChangeDefenseRunner runner = new DefaultStagedChangeDefenseRunner(
                path -> { throw new dev.codedefense.change.GitChangeException(dev.codedefense.change.GitChangeException.Kind.MALFORMED_DATA); },
                new StagedChangeContextBuilder(), new StagedChangePreviewRenderer(),
                () -> { confirmationConstructions.incrementAndGet(); return prompt -> true; },
                out -> { throw new AssertionError("runtime must stay lazy"); }, () -> { throw new AssertionError("passport must stay lazy"); });

        assertEquals(ExitCodes.GIT_EXECUTION_FAILED, runner.run(Path.of("."), false, true,
                new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintWriter(errors, true, StandardCharsets.UTF_8)));
        assertEquals("Git could not safely capture the staged change." + System.lineSeparator(),
                errors.toString(StandardCharsets.UTF_8));
        assertEquals(0, confirmationConstructions.get());
    }

    @Test
    void noEligibleContextDoesNotConstructConfirmation() {
        AtomicInteger confirmationConstructions = new AtomicInteger();
        DefaultStagedChangeDefenseRunner runner = new DefaultStagedChangeDefenseRunner(
                path -> emptyCaptured(path), new StagedChangeContextBuilder(), new StagedChangePreviewRenderer(),
                () -> { confirmationConstructions.incrementAndGet(); return prompt -> true; },
                out -> { throw new AssertionError("runtime must stay lazy"); },
                () -> { throw new AssertionError("passport must stay lazy"); });

        assertEquals(ExitCodes.NO_SUPPORTED_SOURCE_FILES, runner.run(Path.of("."), false, false,
                new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), new PrintWriter(System.err)));
        assertEquals(0, confirmationConstructions.get());
    }

    @Test
    void yesBypassesConfirmationButStillRunsTheStagedWorkflow() {
        AtomicInteger confirmationConstructions = new AtomicInteger();
        AtomicInteger runtimeCalls = new AtomicInteger();
        var passport = PassportTestFixtures.passport(PassportStatus.CURRENT);
        DefaultStagedChangeDefenseRunner runner = new DefaultStagedChangeDefenseRunner(
                this::captured, new StagedChangeContextBuilder(), new StagedChangePreviewRenderer(),
                () -> { confirmationConstructions.incrementAndGet(); return prompt -> false; },
                out -> {
                    runtimeCalls.incrementAndGet();
                    return new CodeDefenseRuntime(snapshot -> { throw new AssertionError(); },
                            () -> (change, snapshot) -> passport.analysis(), analysis -> null,
                            (snapshot, analysis, session) -> { throw new AssertionError(); });
                },
                () -> { throw new AssertionError("no session means no passport"); });

        assertEquals(ExitCodes.SUCCESS, runner.run(Path.of("."), false, true,
                new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), new PrintWriter(System.err)));
        assertEquals(0, confirmationConstructions.get());
        assertEquals(1, runtimeCalls.get());
    }

    private CapturedStagedChange captured(Path ignored) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        StagedChangeFile file = new StagedChangeFile(Path.of("src/App.java"), StagedFileStatus.ADDED, 1, 0);
        StagedChange change = new StagedChange(root, "a".repeat(64), "b".repeat(40), "c".repeat(64),
                "d".repeat(64), List.of(file), 1, 0);
        return new CapturedStagedChange(change,
                List.of(new StagedHunk(file, 0, 0, 1, 1, "+class App {}", false)));
    }

    private CapturedStagedChange emptyCaptured(Path ignored) {
        Path root = Path.of(".").toAbsolutePath().normalize();
        StagedChangeFile file = new StagedChangeFile(Path.of("image.png"), StagedFileStatus.ADDED, 1, 0);
        StagedChange change = new StagedChange(root, "a".repeat(64), "b".repeat(40), "c".repeat(64),
                "d".repeat(64), List.of(file), 1, 0);
        return new CapturedStagedChange(change,
                List.of(new StagedHunk(file, 0, 0, 1, 1, "+not source", false)));
    }
}
