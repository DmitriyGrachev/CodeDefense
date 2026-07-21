package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdkCodexAppServerClientTest {
    @TempDir Path directory;

    @Test
    void performsHandshakeIgnoresNotificationsAndReadsNarrowProjection() {
        try (JdkCodexAppServerClient client = client("fragmented", Duration.ofSeconds(5))) {
            client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.1"), true);
            AppServerThread thread = client.readThread("selected-thread", true);

            assertEquals("C:/repo", thread.cwd());
            assertEquals(1, thread.items().size());
            assertFalse(thread.toString().contains("private-thread-id"));
            assertFalse(thread.toString().contains("PRIVATE-TRANSCRIPT"));
        }
    }

    @Test
    void listsFileChangeItemsAndDrainsLargeStderr() {
        try (JdkCodexAppServerClient client = client("large-stderr", Duration.ofSeconds(5))) {
            client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.1"), true);
            assertEquals(1, client.listThreadItems("selected-thread", 100).size());
        }
    }

    @Test
    void pagesExperimentalItemsToTheRequestedBound() {
        try (JdkCodexAppServerClient client = client("paged", Duration.ofSeconds(5))) {
            client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.1"), true);
            List<AppServerThreadItem> items = client.listThreadItems("selected-thread", 2);
            assertEquals(List.of("src/App.java", "src/B.java"), items.stream()
                    .map(item -> item.fileChanges().getFirst().path()).toList());
        }
    }

    @Test
    void reportsUnsupportedExperimentalMethodWithoutPayload() {
        try (JdkCodexAppServerClient client = client("unsupported", Duration.ofSeconds(5))) {
            client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.1"), true);
            AppServerProtocolException exception = assertThrows(AppServerProtocolException.class,
                    () -> client.listThreadItems("selected-thread", 100));
            assertEquals(AppServerProtocolException.Kind.UNSUPPORTED_METHOD, exception.kind());
            assertFalse(exception.getMessage().contains("PRIVATE"));
        }
    }

    @Test
    void terminatesOnTimeoutAndRejectsInvalidUtf8Safely() {
        try (JdkCodexAppServerClient client = client("timeout", Duration.ofMillis(150))) {
            AppServerProtocolException exception = assertThrows(AppServerProtocolException.class,
                    () -> client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.1"), true));
            assertEquals(AppServerProtocolException.Kind.TIMEOUT, exception.kind());
        }
        try (JdkCodexAppServerClient client = client("invalid-utf8", Duration.ofSeconds(5))) {
            AppServerProtocolException exception = assertThrows(AppServerProtocolException.class,
                    () -> client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.1"), true));
            assertEquals(AppServerProtocolException.Kind.INVALID_RESPONSE, exception.kind());
        }
    }

    @Test
    void rejectsNonzeroExitWithoutRetainingServerDiagnostic() {
        try (JdkCodexAppServerClient client = client("nonzero", Duration.ofSeconds(5))) {
            AppServerProtocolException exception = assertThrows(AppServerProtocolException.class,
                    () -> client.initialize(new AppServerClientInfo("codedefense", "CodeDefense", "0.1.1"), true));
            assertEquals(AppServerProtocolException.Kind.EXECUTION_FAILED, exception.kind());
            assertFalse(exception.getMessage().contains("PRIVATE-SERVER-DIAGNOSTIC"));
        }
    }

    private JdkCodexAppServerClient client(String mode, Duration timeout) {
        return new JdkCodexAppServerClient(new CodexExecutable(List.of(
                javaExecutable().toString(), "-cp", System.getProperty("java.class.path"),
                AppServerFixtureMain.class.getName(), mode)), directory, Map.of(), timeout,
                Duration.ofMillis(100));
    }

    private static Path javaExecutable() {
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }
}
