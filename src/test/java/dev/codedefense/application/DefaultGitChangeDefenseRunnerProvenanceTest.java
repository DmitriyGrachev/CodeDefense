package dev.codedefense.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.change.CapturedGitChange;
import dev.codedefense.change.GitChangeContextBuilder;
import dev.codedefense.change.GitChangePreviewRenderer;
import dev.codedefense.change.GitChangeSource;
import dev.codedefense.change.StagedHunk;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.CodexProvenanceStatus;
import dev.codedefense.domain.CodexProvenanceSummary;
import dev.codedefense.domain.DefenseFocus;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.GitChangeIdentity;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportTestFixtures;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultGitChangeDefenseRunnerProvenanceTest {
    @TempDir Path repository;

    @Test
    void dryRunCapturesPreviewWithoutModelOrPassportAndDoesNotExposeThreadId() {
        AtomicInteger runtimeCalls = new AtomicInteger();
        AtomicInteger passportCalls = new AtomicInteger();
        var fixture = fixture();
        var runner = new DefaultGitChangeDefenseRunner(fixture.source(), new GitChangeContextBuilder(),
                new GitChangePreviewRenderer(), () -> prompt -> false,
                out -> { runtimeCalls.incrementAndGet(); throw new AssertionError("model runtime called"); },
                () -> { passportCalls.incrementAndGet(); throw new AssertionError("passport called"); },
                () -> (root, change, threadId) -> summary());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exit = runner.run(repository, new dev.codedefense.domain.StagedSelector(), DefenseFocus.BALANCED,
                true, false, CodexProvenanceRequest.enabled("private-thread-id"),
                new PrintWriter(output, true, StandardCharsets.UTF_8), new PrintWriter(System.err));

        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(ExitCodes.SUCCESS, exit);
        assertTrue(text.contains("Experimental Codex provenance: Exact change match"));
        assertTrue(text.contains("No source content was sent."));
        assertTrue(text.contains("No model request was made."));
        assertFalse(text.contains("private-thread-id"));
        assertEquals(0, runtimeCalls.get());
        assertEquals(0, passportCalls.get());
    }

    @Test
    void persistedProvenanceDoesNotAlterAuthoritativeInterviewScore() {
        var fixture = fixture();
        var original = PassportTestFixtures.passport(PassportStatus.CURRENT);
        AtomicReference<ChangePassport> saved = new AtomicReference<>();
        ChangePassportStore store = new ChangePassportStore() {
            @Override public Path save(ChangePassport passport) {
                saved.set(passport); return repository.resolve("passport.md").toAbsolutePath();
            }
            @Override public Optional<dev.codedefense.passport.StoredPassportIdentity> readLatestIdentity() {
                return Optional.empty();
            }
        };
        CodeDefenseRuntime runtime = new CodeDefenseRuntime(
                snapshot -> { throw new AssertionError(); },
                () -> (change, snapshot) -> original.analysis(),
                analysis -> original.session(),
                (snapshot, analysis, session) -> { throw new AssertionError(); });
        var runner = new DefaultGitChangeDefenseRunner(fixture.source(), new GitChangeContextBuilder(),
                new GitChangePreviewRenderer(), () -> prompt -> false, out -> runtime,
                () -> new GitChangePassportService(fixture.source(), store, Clock.systemUTC(), "model"),
                () -> (root, change, threadId) -> summary());

        int exit = runner.run(repository, new dev.codedefense.domain.StagedSelector(), DefenseFocus.BALANCED,
                false, true, CodexProvenanceRequest.enabled("private-thread-id"),
                new PrintWriter(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintWriter(System.err));

        assertEquals(ExitCodes.SUCCESS, exit);
        assertEquals(55, saved.get().session().overallScore());
        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH,
                saved.get().codexProvenance().orElseThrow().status());
    }

    private Fixture fixture() {
        StagedChangeFile file = new StagedChangeFile(Path.of("src/App.java"),
                StagedFileStatus.MODIFIED, 1, 1);
        GitChange change = new GitChange(repository.toAbsolutePath().normalize(), "a".repeat(64),
                new GitChangeIdentity(ChangeKind.STAGED, "b".repeat(40), "c".repeat(64), "d".repeat(64)),
                List.of(file), 1, 1);
        CapturedGitChange captured = new CapturedGitChange(change,
                List.of(new StagedHunk(file, 1, 1, 1, 1, "-old\n+new", false)));
        GitChangeSource source = new GitChangeSource() {
            @Override public CapturedGitChange capture(Path path, dev.codedefense.domain.ChangeSelector selector) {
                return captured;
            }
            @Override public GitChangeIdentity captureIdentity(Path path,
                    dev.codedefense.domain.ChangeSelector selector) { return change.identity(); }
        };
        return new Fixture(source);
    }

    private static CodexProvenanceSummary summary() {
        return new CodexProvenanceSummary(1, CodexProvenanceStatus.EXACT_CHANGE_MATCH,
                "e".repeat(64), "0.144.0", 1, 1, List.of("src/App.java"), Instant.EPOCH);
    }

    private record Fixture(GitChangeSource source) {}
}
