package dev.codedefense.jetbrains.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.codedefense.jetbrains.fixture.FakeBridgeMain;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BridgeProcessTest {
    @Test
    void exchangesRequestsAndEventsWhileDrainingLargeStderr() throws Exception {
        Process child = process("exchange").start();
        List<String> eventTypes = new CopyOnWriteArrayList<>();
        BridgeProcess bridge = new BridgeProcess(child, new BridgeLineCodec(1), event -> eventTypes.add(event.type()),
                Duration.ofSeconds(2));

        bridge.sendAnswer("answer with spaces");

        assertEquals(0, bridge.completion().get(10, TimeUnit.SECONDS));
        assertEquals(List.of("hello", "completed"), eventTypes);
        assertFalse(child.isAlive());
    }

    @Test
    void cancelTerminatesSleepingChildWithinBoundedGracePeriod() throws Exception {
        Process child = process("sleep").start();
        BridgeProcess bridge = new BridgeProcess(child, new BridgeLineCodec(), event -> { },
                Duration.ofMillis(100));

        bridge.cancel();

        assertTrue(bridge.completion().get(5, TimeUnit.SECONDS) != Integer.MIN_VALUE);
        assertFalse(child.isAlive());
    }

    @Test
    void retainsNonzeroExitAndRejectsMalformedOrOversizedStdout() throws Exception {
        BridgeProcess failed = bridge("fail");
        assertEquals(23, failed.completion().get(5, TimeUnit.SECONDS));

        BridgeProcess malformed = bridge("malformed");
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> malformed.completion().get(5, TimeUnit.SECONDS));

        BridgeProcess oversized = bridge("oversized");
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> oversized.completion().get(5, TimeUnit.SECONDS));
    }

    @Test
    void retriesExactlyOnceWithProtocolOneAfterCleanUnsupportedVersionExit() throws Exception {
        List<Integer> attempts = new CopyOnWriteArrayList<>();
        BridgeProcess bridge = new BridgeProcess(version -> {
            attempts.add(version);
            return version == 2
                    ? process("rejectProtocol").start()
                    : process("exchangeVersion", "1", "skip").start();
        }, event -> { }, Duration.ofMillis(200));

        awaitProtocol(bridge, 1);
        bridge.sendSkip();

        assertEquals(0, bridge.completion().get(10, TimeUnit.SECONDS));
        assertEquals(List.of(2, 1), attempts);
        assertEquals(1, bridge.protocolVersion());
    }

    @Test
    void retriesLegacyExactUnsupportedVersionEventWithoutDeliveringIt() throws Exception {
        List<Integer> attempts = new CopyOnWriteArrayList<>();
        List<String> delivered = new CopyOnWriteArrayList<>();
        BridgeProcess bridge = new BridgeProcess(version -> {
            attempts.add(version);
            return version == 2
                    ? process("legacyUnsupported").start()
                    : process("exchangeVersion", "1", "skip").start();
        }, event -> delivered.add(event.type()), Duration.ofMillis(200));

        awaitProtocol(bridge, 1);
        bridge.sendSkip();

        assertEquals(0, bridge.completion().get(10, TimeUnit.SECONDS));
        assertEquals(List.of(2, 1), attempts);
        assertEquals(List.of("hello", "completed"), delivered);
    }

    @Test
    void protocolTwoAttemptControlsOutboundRequestEncoding() throws Exception {
        List<Integer> attempts = new CopyOnWriteArrayList<>();
        BridgeProcess bridge = new BridgeProcess(version -> {
            attempts.add(version);
            return process("exchangeVersion", Integer.toString(version), "answer").start();
        }, event -> { }, Duration.ofMillis(200));

        awaitProtocol(bridge, 2);
        bridge.sendAnswer("bounded answer");

        assertEquals(0, bridge.completion().get(10, TimeUnit.SECONDS));
        assertEquals(List.of(2), attempts);
        assertEquals(2, bridge.protocolVersion());
    }

    @Test
    void neverRetriesAfterAnyValidEvent() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        BridgeProcess bridge = new BridgeProcess(version -> {
            attempts.incrementAndGet();
            return process("eventThenInvalid", Integer.toString(version)).start();
        }, event -> { }, Duration.ofMillis(200));

        assertEquals(2, bridge.completion().get(10, TimeUnit.SECONDS));
        assertEquals(1, attempts.get());
    }

    @Test
    void rejectsAnyOtherWrongVersionEventWithoutRetryOrDelivery() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<String> delivered = new CopyOnWriteArrayList<>();
        BridgeProcess bridge = new BridgeProcess(version -> {
            attempts.incrementAndGet();
            return process("wrongVersionHello").start();
        }, event -> delivered.add(event.type()), Duration.ofMillis(200));

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> bridge.completion().get(10, TimeUnit.SECONDS));
        assertEquals(1, attempts.get());
        assertTrue(delivered.isEmpty());
    }

    @Test
    void outboundRequestDisqualifiesFallbackAndIsNotSilentlyLost() throws Exception {
        List<Integer> attempts = new CopyOnWriteArrayList<>();
        BridgeProcess bridge = new BridgeProcess(version -> {
            attempts.add(version);
            return version == 2 ? process("requestThenInvalid").start() : process("fail").start();
        }, event -> { }, Duration.ofMillis(200));

        bridge.sendSkip();

        assertEquals(2, bridge.completion().get(10, TimeUnit.SECONDS));
        assertEquals(List.of(2), attempts);
    }

    @Test
    void cancelDuringNegotiationNeverStartsFallback() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        BridgeProcess bridge = new BridgeProcess(version -> {
            attempts.incrementAndGet();
            return process("sleep").start();
        }, event -> { }, Duration.ofMillis(100));

        bridge.cancel();
        bridge.completion().get(10, TimeUnit.SECONDS);

        assertEquals(1, attempts.get());
    }

    @Test
    void neverRetriesMalformedOutputOrOtherExitCodes() throws Exception {
        AtomicInteger malformedAttempts = new AtomicInteger();
        BridgeProcess malformed = new BridgeProcess(version -> {
            malformedAttempts.incrementAndGet();
            return process("malformed").start();
        }, event -> { }, Duration.ofMillis(200));
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> malformed.completion().get(10, TimeUnit.SECONDS));
        assertEquals(1, malformedAttempts.get());

        AtomicInteger failedAttempts = new AtomicInteger();
        BridgeProcess failed = new BridgeProcess(version -> {
            failedAttempts.incrementAndGet();
            return process("fail").start();
        }, event -> { }, Duration.ofMillis(200));
        assertEquals(23, failed.completion().get(10, TimeUnit.SECONDS));
        assertEquals(1, failedAttempts.get());
    }

    private BridgeProcess bridge(String mode) throws Exception {
        return new BridgeProcess(process(mode).start(), new BridgeLineCodec(), event -> { },
                Duration.ofMillis(200));
    }

    private ProcessBuilder process(String mode) {
        return process(new String[] {mode});
    }

    private ProcessBuilder process(String... arguments) {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java");
        String fixtureClasses = Path.of("build", "classes", "java", "test")
                .toAbsolutePath().normalize().toString();
        List<String> command = new java.util.ArrayList<>(List.of(
                java.toString(), "-cp", fixtureClasses, FakeBridgeMain.class.getName()));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command);
    }

    private void awaitProtocol(BridgeProcess bridge, int protocolVersion) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (bridge.protocolVersion() != protocolVersion && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(protocolVersion, bridge.protocolVersion());
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
