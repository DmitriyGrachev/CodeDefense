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
import org.junit.jupiter.api.Test;

class BridgeProcessTest {
    @Test
    void exchangesRequestsAndEventsWhileDrainingLargeStderr() throws Exception {
        Process child = process("exchange").start();
        List<String> eventTypes = new CopyOnWriteArrayList<>();
        BridgeProcess bridge = new BridgeProcess(child, new BridgeLineCodec(), event -> eventTypes.add(event.type()),
                Duration.ofSeconds(2));

        bridge.send(new BridgeLineCodec().answerRequest("answer with spaces"));

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

    private BridgeProcess bridge(String mode) throws Exception {
        return new BridgeProcess(process(mode).start(), new BridgeLineCodec(), event -> { },
                Duration.ofMillis(200));
    }

    private ProcessBuilder process(String mode) {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java");
        String fixtureClasses = Path.of("build", "classes", "java", "test")
                .toAbsolutePath().normalize().toString();
        return new ProcessBuilder(java.toString(), "-cp", fixtureClasses, FakeBridgeMain.class.getName(), mode);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
