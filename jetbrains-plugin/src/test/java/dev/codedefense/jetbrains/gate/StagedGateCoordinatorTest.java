package dev.codedefense.jetbrains.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StagedGateCoordinatorTest {
    @Test
    void coalescesSignalsAtExactlySevenHundredFiftyMillisecondsAndUsesLatestGeneration() {
        var scheduler = new FakeScheduler();
        var loader = new FakeLoader();
        var coordinator = new StagedGateCoordinator(scheduler, loader);

        coordinator.requestRefresh();
        coordinator.requestRefresh();
        coordinator.requestRefresh();

        assertEquals(List.of(Duration.ofMillis(750), Duration.ofMillis(750), Duration.ofMillis(750)),
                scheduler.delays());
        assertTrue(scheduler.tasks.get(0).cancelled);
        assertTrue(scheduler.tasks.get(1).cancelled);
        scheduler.runLatest();
        assertEquals(List.of(3L), loader.generations());
    }

    @Test
    void allowsOneLoadAndSuppressesStaleResultBeforeStartingReadyRefresh() {
        var scheduler = new FakeScheduler();
        var loader = new FakeLoader();
        var coordinator = new StagedGateCoordinator(scheduler, loader);
        List<StagedGateView> observed = new ArrayList<>();
        coordinator.addObserver(observed::add);

        coordinator.requestRefresh();
        scheduler.runLatest();
        coordinator.requestRefresh();
        scheduler.runLatest();

        assertEquals(List.of(1L), loader.generations());
        loader.loads.get(0).complete(current(1));
        assertTrue(observed.isEmpty());
        assertEquals(List.of(1L, 2L), loader.generations());
        loader.loads.get(1).complete(current(2));
        assertEquals(List.of(current(2)), observed);
    }

    @Test
    void waitsForRemainingDebounceWhenInFlightLoadFinishesBeforeNewTimer() {
        var scheduler = new FakeScheduler();
        var loader = new FakeLoader();
        var coordinator = new StagedGateCoordinator(scheduler, loader);

        coordinator.requestRefresh();
        scheduler.runLatest();
        coordinator.requestRefresh();
        loader.loads.get(0).complete(current(1));

        assertEquals(List.of(1L), loader.generations());
        scheduler.runLatest();
        assertEquals(List.of(1L, 2L), loader.generations());
    }

    @Test
    void removedObserverReceivesNoFurtherValidatedViews() {
        var scheduler = new FakeScheduler();
        var loader = new FakeLoader();
        var coordinator = new StagedGateCoordinator(scheduler, loader);
        List<StagedGateView> first = new ArrayList<>();
        List<StagedGateView> second = new ArrayList<>();
        StagedGateCoordinator.Observer removed = first::add;
        coordinator.addObserver(removed);
        coordinator.addObserver(second::add);
        coordinator.removeObserver(removed);

        coordinator.requestRefresh();
        scheduler.runLatest();
        loader.loads.get(0).complete(current(1));

        assertTrue(first.isEmpty());
        assertEquals(List.of(current(1)), second);
    }

    @Test
    void disposalCancelsPendingAndInflightWorkAndPreventsPublication() {
        var pendingScheduler = new FakeScheduler();
        var pendingLoader = new FakeLoader();
        var pending = new StagedGateCoordinator(pendingScheduler, pendingLoader);
        pending.requestRefresh();
        pending.dispose();
        assertTrue(pendingScheduler.tasks.get(0).cancelled);
        pendingScheduler.runLatest();
        assertTrue(pendingLoader.loads.isEmpty());

        var scheduler = new FakeScheduler();
        var loader = new FakeLoader();
        var coordinator = new StagedGateCoordinator(scheduler, loader);
        List<StagedGateView> observed = new ArrayList<>();
        coordinator.addObserver(observed::add);
        coordinator.requestRefresh();
        scheduler.runLatest();

        coordinator.dispose();
        assertTrue(loader.loads.get(0).cancelled);
        loader.loads.get(0).complete(current(1));
        assertTrue(observed.isEmpty());
        coordinator.requestRefresh();
        assertEquals(1, scheduler.tasks.size());
        assertTrue(loader.loads.get(0).completion.isDone());
    }

    @Test
    void observerRemovalWaitsForActiveCallbackAndPreventsLaterDelivery() throws Exception {
        var scheduler = new FakeScheduler();
        var loader = new FakeLoader();
        var coordinator = new StagedGateCoordinator(scheduler, loader);
        var observer = new BlockingObserver();
        coordinator.addObserver(observer);
        coordinator.requestRefresh();
        scheduler.runLatest();
        Thread publisher = Thread.ofVirtual().start(() -> loader.loads.get(0).complete(current(1)));
        assertTrue(observer.entered.await(2, TimeUnit.SECONDS));
        CountDownLatch removalStarted = new CountDownLatch(1);
        CountDownLatch removalReturned = new CountDownLatch(1);
        Thread remover = Thread.ofVirtual().start(() -> {
            removalStarted.countDown();
            coordinator.removeObserver(observer);
            removalReturned.countDown();
        });
        assertTrue(removalStarted.await(2, TimeUnit.SECONDS));

        assertFalse(removalReturned.await(100, TimeUnit.MILLISECONDS));
        observer.release.countDown();
        assertTrue(removalReturned.await(2, TimeUnit.SECONDS));
        publisher.join(2_000);
        remover.join(2_000);

        coordinator.requestRefresh();
        scheduler.runLatest();
        loader.loads.get(1).complete(current(2));
        assertEquals(1, observer.calls.get());
    }

    @Test
    void disposalWaitsForActiveCallbackAndPreventsLaterDelivery() throws Exception {
        var scheduler = new FakeScheduler();
        var loader = new FakeLoader();
        var coordinator = new StagedGateCoordinator(scheduler, loader);
        var observer = new BlockingObserver();
        coordinator.addObserver(observer);
        coordinator.requestRefresh();
        scheduler.runLatest();
        Thread publisher = Thread.ofVirtual().start(() -> loader.loads.get(0).complete(current(1)));
        assertTrue(observer.entered.await(2, TimeUnit.SECONDS));
        CountDownLatch disposalStarted = new CountDownLatch(1);
        CountDownLatch disposalReturned = new CountDownLatch(1);
        Thread disposer = Thread.ofVirtual().start(() -> {
            disposalStarted.countDown();
            coordinator.dispose();
            disposalReturned.countDown();
        });
        assertTrue(disposalStarted.await(2, TimeUnit.SECONDS));

        assertFalse(disposalReturned.await(100, TimeUnit.MILLISECONDS));
        observer.release.countDown();
        assertTrue(disposalReturned.await(2, TimeUnit.SECONDS));
        publisher.join(2_000);
        disposer.join(2_000);
        assertEquals(1, observer.calls.get());
        coordinator.requestRefresh();
        assertEquals(1, scheduler.tasks.size());
    }

    private StagedGateView current(int attempt) {
        return new StagedGateView(1, StagedGateView.State.CURRENT, StagedGateView.Reason.IDENTITY_MATCH,
                "abcdef0123456789".repeat(4), attempt, 1, 2, 0, List.of());
    }

    private static final class FakeScheduler implements StagedGateCoordinator.Scheduler {
        private final List<FakeScheduledTask> tasks = new ArrayList<>();

        @Override public StagedGateCoordinator.ScheduledTask schedule(Duration delay, Runnable task) {
            var scheduled = new FakeScheduledTask(delay, task);
            tasks.add(scheduled);
            return scheduled;
        }

        private List<Duration> delays() {
            return tasks.stream().map(task -> task.delay).toList();
        }

        private void runLatest() {
            FakeScheduledTask task = tasks.getLast();
            if (!task.cancelled) task.action.run();
        }
    }

    private static final class FakeScheduledTask implements StagedGateCoordinator.ScheduledTask {
        private final Duration delay;
        private final Runnable action;
        private boolean cancelled;

        private FakeScheduledTask(Duration delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }

        @Override public void cancel() {
            cancelled = true;
        }
    }

    private static final class FakeLoader implements StagedGateCoordinator.Loader {
        private final List<Long> generations = new ArrayList<>();
        private final List<FakeLoad> loads = new ArrayList<>();

        @Override public StagedGateCoordinator.Load load(long generation) {
            generations.add(generation);
            var load = new FakeLoad();
            loads.add(load);
            return load;
        }

        private List<Long> generations() {
            return List.copyOf(generations);
        }
    }

    private static final class FakeLoad implements StagedGateCoordinator.Load {
        private final CompletableFuture<StagedGateView> completion = new CompletableFuture<>();
        private boolean cancelled;

        @Override public CompletableFuture<StagedGateView> completion() {
            return completion;
        }

        @Override public void cancel() {
            cancelled = true;
        }

        private void complete(StagedGateView view) {
            completion.complete(view);
        }
    }

    private static final class BlockingObserver implements StagedGateCoordinator.Observer {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        @Override public void onGateChanged(StagedGateView view) {
            calls.incrementAndGet();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
