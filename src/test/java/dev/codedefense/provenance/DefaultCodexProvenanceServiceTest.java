package dev.codedefense.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.ai.AppServerClientInfo;
import dev.codedefense.ai.AppServerFileChange;
import dev.codedefense.ai.AppServerProtocolException;
import dev.codedefense.ai.AppServerThread;
import dev.codedefense.ai.AppServerThreadItem;
import dev.codedefense.ai.CodexAppServerClient;
import dev.codedefense.ai.CodexEnvironment;
import dev.codedefense.ai.CodexExecutable;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.change.CapturedGitChange;
import dev.codedefense.change.StagedHunk;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.CodexProvenanceStatus;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.GitChangeIdentity;
import dev.codedefense.domain.StagedChangeFile;
import dev.codedefense.domain.StagedFileStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultCodexProvenanceServiceTest {
    @TempDir Path repository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void capturesOnlyNamedThreadInRequiredOrderAndHashesItsIdentity() {
        List<String> calls = new ArrayList<>();
        FakeClient client = new FakeClient(repository, calls);
        DefaultCodexProvenanceService service = service(true, "0.144.0", client, calls);

        var summary = service.capture(repository, change(), "private-thread-id");

        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH, summary.status());
        assertEquals(DefaultCodexProvenanceService.threadHash("private-thread-id"),
                summary.threadIdentityHash());
        assertEquals(List.of("preflight", "client", "initialize", "read:false", "list", "close"), calls);
    }

    @Test
    void disabledKillSwitchAndUnsupportedVersionReturnUnavailableWithoutHistoryRead() {
        AtomicInteger clients = new AtomicInteger();
        DefaultCodexProvenanceService disabled = new DefaultCodexProvenanceService(
                config(false), () -> { throw new AssertionError("preflight called"); }, new CodexChangeMatcher(),
                (environment, root, config) -> { clients.incrementAndGet(); throw new AssertionError(); }, clock);
        assertEquals(CodexProvenanceStatus.UNAVAILABLE,
                disabled.capture(repository, change(), "thread").status());
        assertEquals(0, clients.get());

        List<String> calls = new ArrayList<>();
        DefaultCodexProvenanceService unsupported = service(true, "9.9.9",
                new FakeClient(repository, calls), calls);
        assertEquals(CodexProvenanceStatus.UNAVAILABLE,
                unsupported.capture(repository, change(), "thread").status());
        assertEquals(List.of("preflight"), calls);
    }

    @Test
    void cwdMismatchIsNoMatchAndUnsupportedItemsFallsBackOnce() {
        List<String> mismatchCalls = new ArrayList<>();
        FakeClient mismatch = new FakeClient(repository.resolve("other"), mismatchCalls);
        var noMatch = service(true, "0.144.0", mismatch, mismatchCalls)
                .capture(repository, change(), "thread");
        assertEquals(CodexProvenanceStatus.NO_MATCH, noMatch.status());

        List<String> calls = new ArrayList<>();
        FakeClient fallback = new FakeClient(repository, calls);
        fallback.unsupportedItems = true;
        var exact = service(true, "0.144.0", fallback, calls)
                .capture(repository, change(), "thread");
        assertEquals(CodexProvenanceStatus.EXACT_CHANGE_MATCH, exact.status());
        assertEquals(List.of("preflight", "client", "initialize", "read:false", "list", "read:true", "close"), calls);
    }

    @Test
    void interruptionPropagatesAndStillClosesClient() {
        List<String> calls = new ArrayList<>();
        FakeClient client = new FakeClient(repository, calls);
        client.interrupt = true;
        assertThrows(CodexInterruptedException.class,
                () -> service(true, "0.144.0", client, calls).capture(repository, change(), "thread"));
        assertEquals("close", calls.getLast());
    }

    @Test
    void moreThanOneHundredRelevantPathsIsUnavailable() {
        List<String> calls = new ArrayList<>();
        FakeClient client = new FakeClient(repository, calls);
        client.returnedItems = java.util.stream.IntStream.rangeClosed(1, 101)
                .mapToObj(index -> new AppServerThreadItem("change-" + index, "fileChange", List.of(
                        new AppServerFileChange("change-" + index, "src/File" + index + ".java",
                                "update", "@@ -1 +1 @@\n-old\n+new"))))
                .toList();

        var summary = service(true, "0.144.0", client, calls)
                .capture(repository, change(), "thread");

        assertEquals(CodexProvenanceStatus.UNAVAILABLE, summary.status());
    }

    private DefaultCodexProvenanceService service(boolean enabled, String version,
            FakeClient client, List<String> calls) {
        return new DefaultCodexProvenanceService(config(enabled), () -> {
            calls.add("preflight");
            return new CodexEnvironment(new CodexExecutable(List.of("codex")), version);
        }, new CodexChangeMatcher(), (environment, root, value) -> {
            calls.add("client"); return client;
        }, clock);
    }

    private static CodexProvenanceConfig config(boolean enabled) {
        return new CodexProvenanceConfig(enabled, Duration.ofSeconds(1), Duration.ofMillis(50),
                1_000, Set.of("0.143.0", "0.144.0"));
    }

    private CapturedGitChange change() {
        StagedChangeFile file = new StagedChangeFile(Path.of("src/App.java"),
                StagedFileStatus.MODIFIED, 1, 1);
        GitChange value = new GitChange(repository.toAbsolutePath().normalize(), "a".repeat(64),
                new GitChangeIdentity(ChangeKind.STAGED, "b".repeat(40), "c".repeat(64), "d".repeat(64)),
                List.of(file), 1, 1);
        return new CapturedGitChange(value,
                List.of(new StagedHunk(file, 1, 1, 1, 1, "-old\n+new", false)));
    }

    private static final class FakeClient implements CodexAppServerClient {
        private final Path cwd; private final List<String> calls;
        boolean unsupportedItems; boolean interrupt;
        List<AppServerThreadItem> returnedItems;
        FakeClient(Path cwd, List<String> calls) { this.cwd = cwd; this.calls = calls; }
        @Override public void initialize(AppServerClientInfo info, boolean experimentalApi) {
            calls.add("initialize");
            if (interrupt) throw new CodexInterruptedException(new InterruptedException());
        }
        @Override public AppServerThread readThread(String threadId, boolean includeTurns) {
            calls.add("read:" + includeTurns);
            return new AppServerThread(threadId, cwd.toAbsolutePath().normalize().toString(), "cli",
                    includeTurns ? items() : List.of());
        }
        @Override public List<AppServerThreadItem> listThreadItems(String threadId, int limit) {
            calls.add("list");
            if (unsupportedItems) throw new AppServerProtocolException(
                    AppServerProtocolException.Kind.UNSUPPORTED_METHOD);
            return returnedItems == null ? items() : returnedItems;
        }
        private static List<AppServerThreadItem> items() {
            return List.of(new AppServerThreadItem("change-1", "fileChange", List.of(
                    new AppServerFileChange("change-1", "src/App.java", "update",
                            "@@ -1 +1 @@\n-old\n+new"))));
        }
        @Override public void close() { calls.add("close"); }
    }
}
