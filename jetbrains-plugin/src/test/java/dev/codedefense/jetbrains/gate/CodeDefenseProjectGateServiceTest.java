package dev.codedefense.jetbrains.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CodeDefenseProjectGateServiceTest {
    @Test
    void cachedStartsEmptyAndFreshWaitsForANewPublication() throws Exception {
        ImmediateScheduler scheduler = new ImmediateScheduler();
        List<StagedGateView> results = new ArrayList<>(List.of(noChange(), current()));
        var coordinator = new StagedGateCoordinator(scheduler,
                generation -> completed(results.removeFirst()));
        var service = new CodeDefenseProjectGateService(coordinator, () -> { });

        assertTrue(service.cached().isEmpty());
        service.requestRefresh();
        assertEquals(StagedGateView.State.NO_STAGED_CHANGE, service.cached().orElseThrow().state());

        StagedGateView fresh = service.fresh(Duration.ofSeconds(1));

        assertEquals(StagedGateView.State.CURRENT, fresh.state());
        assertEquals(StagedGateView.State.CURRENT, service.cached().orElseThrow().state());
        assertEquals(2, scheduler.scheduled);
    }

    @Test
    void freshReturnsSafeUnavailableWithinBoundWhenNoGenerationCompletes() {
        var coordinator = new StagedGateCoordinator((delay, task) -> () -> { },
                generation -> new StagedGateCoordinator.Load() {
                    @Override public CompletableFuture<StagedGateView> completion() {
                        return new CompletableFuture<>();
                    }
                    @Override public void cancel() { }
                });
        var service = new CodeDefenseProjectGateService(coordinator, () -> { });
        long started = System.nanoTime();

        StagedGateView result = service.fresh(Duration.ofMillis(40));

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertEquals(StagedGateView.State.UNAVAILABLE, result.state());
        assertEquals(StagedGateView.Reason.GIT_CAPTURE_FAILED, result.reason());
        assertTrue(elapsedMillis < 500);
        assertTrue(service.cached().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.fresh(Duration.ZERO));
    }

    @Test
    void disposingRemovesObserversAndStopsOwnedCoordinator() {
        ImmediateScheduler scheduler = new ImmediateScheduler();
        var coordinator = new StagedGateCoordinator(scheduler, generation -> completed(current()));
        int[] observed = {0};
        int[] shutdowns = {0};
        var service = new CodeDefenseProjectGateService(coordinator, () -> shutdowns[0]++);
        StagedGateCoordinator.Observer observer = gate -> observed[0]++;
        service.addObserver(observer);
        service.requestRefresh();
        assertEquals(1, observed[0]);

        service.dispose();
        service.requestRefresh();

        assertEquals(1, observed[0]);
        assertEquals(1, shutdowns[0]);
    }

    private static StagedGateCoordinator.Load completed(StagedGateView view) {
        return new StagedGateCoordinator.Load() {
            @Override public CompletableFuture<StagedGateView> completion() {
                return CompletableFuture.completedFuture(view);
            }
            @Override public void cancel() { }
        };
    }

    private static StagedGateView noChange() {
        return new StagedGateView(1, StagedGateView.State.NO_STAGED_CHANGE,
                StagedGateView.Reason.NO_INDEX_ENTRIES, "", 0, 0, 0, 0, List.of());
    }

    private static StagedGateView current() {
        return new StagedGateView(1, StagedGateView.State.CURRENT,
                StagedGateView.Reason.IDENTITY_MATCH, "a".repeat(64), 1, 1, 2, 3, List.of());
    }

    private static final class ImmediateScheduler implements StagedGateCoordinator.Scheduler {
        private int scheduled;

        @Override public StagedGateCoordinator.ScheduledTask schedule(Duration delay, Runnable task) {
            scheduled++;
            task.run();
            return () -> { };
        }
    }
}
