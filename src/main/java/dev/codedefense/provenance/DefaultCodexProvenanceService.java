package dev.codedefense.provenance;

import dev.codedefense.ai.AppServerClientInfo;
import dev.codedefense.ai.AppServerFileChange;
import dev.codedefense.ai.AppServerProtocolException;
import dev.codedefense.ai.AppServerThread;
import dev.codedefense.ai.AppServerThreadItem;
import dev.codedefense.ai.CodexAppServerClient;
import dev.codedefense.ai.CodexEnvironment;
import dev.codedefense.ai.CodexPreflight;
import dev.codedefense.ai.CodexProcessEnvironment;
import dev.codedefense.ai.JdkCodexAppServerClient;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.change.CapturedGitChange;
import dev.codedefense.domain.CodexProvenanceStatus;
import dev.codedefense.domain.CodexProvenanceSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DefaultCodexProvenanceService implements CodexProvenanceService {
    private static final int MAXIMUM_RELEVANT_PATHS = 100;

    @FunctionalInterface
    interface ClientFactory {
        CodexAppServerClient create(CodexEnvironment environment, Path repository,
                CodexProvenanceConfig config);
    }

    private final CodexProvenanceConfig config;
    private final CodexPreflight preflight;
    private final CodexChangeMatcher matcher;
    private final ClientFactory clientFactory;
    private final Clock clock;

    public DefaultCodexProvenanceService(CodexProvenanceConfig config, CodexPreflight preflight,
            CodexChangeMatcher matcher) {
        this(config, preflight, matcher, (environment, repository, value) ->
                new JdkCodexAppServerClient(environment.executable(), repository,
                        new CodexProcessEnvironment().sanitize(System.getenv()),
                        value.timeout(), value.terminationGracePeriod()), Clock.systemUTC());
    }

    DefaultCodexProvenanceService(CodexProvenanceConfig config, CodexPreflight preflight,
            CodexChangeMatcher matcher, ClientFactory clientFactory, Clock clock) {
        this.config = Objects.requireNonNull(config);
        this.preflight = Objects.requireNonNull(preflight);
        this.matcher = Objects.requireNonNull(matcher);
        this.clientFactory = Objects.requireNonNull(clientFactory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public CodexProvenanceSummary capture(Path repository,
            CapturedGitChange change, String threadId) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(change, "change");
        int selected = change.change().files().size();
        if (!config.enabled()) return unavailable(selected);
        String selectedThread = requireThreadId(threadId);
        try {
            Path root = repository.toRealPath();
            CodexEnvironment environment = preflight.checkReady();
            if (!config.supportedCodexVersions().contains(environment.version())) return unavailable(selected);
            try (CodexAppServerClient client = clientFactory.create(environment, root, config)) {
                client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.0"), true);
                AppServerThread metadata = client.readThread(selectedThread, false);
                if (!sameRepository(root, metadata.cwd())) {
                    return summary(CodexProvenanceStatus.NO_MATCH, selectedThread, environment.version(),
                            selected, List.of());
                }
                List<AppServerThreadItem> items;
                try {
                    items = client.listThreadItems(selectedThread, config.maximumItems());
                } catch (AppServerProtocolException exception) {
                    if (exception.kind() != AppServerProtocolException.Kind.UNSUPPORTED_METHOD) throw exception;
                    items = client.readThread(selectedThread, true).items();
                }
                List<AppServerFileChange> changes = items.stream()
                        .flatMap(item -> item.fileChanges().stream()).limit(config.maximumItems()).toList();
                long relevantPathCount = changes.stream().map(AppServerFileChange::path)
                        .distinct().limit(MAXIMUM_RELEVANT_PATHS + 1L).count();
                if (relevantPathCount > MAXIMUM_RELEVANT_PATHS) return unavailable(selected);
                ProvenanceMatch match;
                try {
                    match = matcher.match(change, changes);
                } catch (IllegalArgumentException exception) {
                    return unavailable(selected);
                }
                return summary(match.status(), selectedThread, environment.version(),
                        match.selectedFileCount(), match.matchedRelativePaths());
            }
        } catch (CodexNotInstalledException | CodexNotAuthenticatedException
                | AppServerProtocolException | IOException exception) {
            return unavailable(selected);
        }
    }

    private CodexProvenanceSummary summary(CodexProvenanceStatus status, String threadId,
            String version, int selected, List<String> paths) {
        return new CodexProvenanceSummary(1, status, threadHash(threadId), version,
                selected, paths.size(), paths, clock.instant());
    }

    private CodexProvenanceSummary unavailable(int selected) {
        return new CodexProvenanceSummary(1, CodexProvenanceStatus.UNAVAILABLE, "", "",
                selected, 0, List.of(), clock.instant());
    }

    private static boolean sameRepository(Path repository, String cwd) {
        try { return repository.equals(Path.of(cwd).toRealPath()); }
        catch (IOException | RuntimeException exception) { return false; }
    }

    private static String requireThreadId(String value) {
        Objects.requireNonNull(value, "threadId");
        if (value.isBlank() || value.length() > 512 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("thread ID is invalid");
        }
        return value;
    }

    static String threadHash(String threadId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("codedefense-thread-v1\0".getBytes(StandardCharsets.UTF_8));
            digest.update(threadId.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    @Override public String toString() { return "DefaultCodexProvenanceService[enabled=%s]".formatted(config.enabled()); }
}
