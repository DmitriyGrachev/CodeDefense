package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.ai.exception.CodexNotInstalledException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CodexCliAiProviderTest {
    @Test
    void cachesOnlySuccessfulPreflightAndPassesRequestAndResultThrough() {
        AtomicInteger preflightCalls = new AtomicInteger();
        AtomicReference<StructuredCodexRequest> receivedRequest = new AtomicReference<>();
        CodexEnvironment environment = environment();
        StructuredCodexResult expected = new StructuredCodexResult("{\"ok\":true}", Duration.ZERO, "model");
        CodexCliAiProvider provider = new CodexCliAiProvider(
                () -> {
                    preflightCalls.incrementAndGet();
                    return environment;
                },
                (ready, request) -> {
                    assertEquals(environment, ready);
                    receivedRequest.set(request);
                    return expected;
                });
        StructuredCodexRequest request = request();

        assertEquals(expected, provider.execute(request));
        assertEquals(expected, provider.execute(request));
        assertEquals(request, receivedRequest.get());
        assertEquals(1, preflightCalls.get());
    }

    @Test
    void retriesFailedPreflightOnTheNextExecution() {
        AtomicInteger attempts = new AtomicInteger();
        CodexCliAiProvider provider = new CodexCliAiProvider(
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new CodexNotInstalledException();
                    }
                    return environment();
                },
                (ready, request) -> new StructuredCodexResult("{}", Duration.ZERO, request.model()));

        assertThrows(CodexNotInstalledException.class, () -> provider.execute(request()));
        provider.execute(request());

        assertEquals(2, attempts.get());
    }

    @Test
    void concurrentInitialExecutionsShareOneSuccessfulPreflight() throws Exception {
        AtomicInteger preflightCalls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CodexCliAiProvider provider = new CodexCliAiProvider(
                () -> {
                    preflightCalls.incrementAndGet();
                    return environment();
                },
                (ready, request) -> new StructuredCodexResult("{}", Duration.ZERO, request.model()));

        Thread first = Thread.ofVirtual().start(() -> awaitAndExecute(start, provider));
        Thread second = Thread.ofVirtual().start(() -> awaitAndExecute(start, provider));
        start.countDown();
        first.join();
        second.join();

        assertEquals(1, preflightCalls.get());
    }

    private static void awaitAndExecute(CountDownLatch start, CodexCliAiProvider provider) {
        try {
            start.await();
            provider.execute(request());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static CodexEnvironment environment() {
        return new CodexEnvironment(new CodexExecutable(List.of("codex")), "version");
    }

    private static StructuredCodexRequest request() {
        return new StructuredCodexRequest(
                "operation", "private prompt", "{}", "model", ReasoningEffort.LOW, Duration.ofSeconds(1));
    }
}
