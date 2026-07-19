package dev.codedefense.jetbrains.gate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Coalesces staged gate refresh signals and publishes only the newest completed generation. */
public final class StagedGateCoordinator implements AutoCloseable {
    public static final Duration DEBOUNCE = Duration.ofMillis(750);

    private final Scheduler scheduler;
    private final Loader loader;
    private final Map<Observer, ObserverRegistration> observers = new LinkedHashMap<>();
    private ScheduledTask scheduled;
    private Load activeLoad;
    private long requestedGeneration;
    private long loadingGeneration;
    private long readyGeneration;
    private boolean loading;
    private boolean disposed;

    public StagedGateCoordinator(Scheduler scheduler, Loader loader) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public synchronized void requestRefresh() {
        if (disposed) return;
        long generation = ++requestedGeneration;
        if (scheduled != null) safeCancel(scheduled);
        scheduled = Objects.requireNonNull(scheduler.schedule(DEBOUNCE, () -> debounceElapsed(generation)),
                "scheduledTask");
    }

    public synchronized void addObserver(Observer observer) {
        if (!disposed) {
            Observer validated = Objects.requireNonNull(observer, "observer");
            observers.computeIfAbsent(validated, ObserverRegistration::new);
        }
    }

    public void removeObserver(Observer observer) {
        ObserverRegistration registration;
        synchronized (this) {
            registration = observers.remove(observer);
        }
        if (registration != null) registration.deactivateAndAwait();
    }

    public void dispose() {
        ScheduledTask pending;
        Load running;
        List<ObserverRegistration> registrations;
        synchronized (this) {
            if (disposed) return;
            disposed = true;
            pending = scheduled;
            running = activeLoad;
            scheduled = null;
            activeLoad = null;
            registrations = new ArrayList<>(observers.values());
            observers.clear();
        }
        safeCancel(pending);
        safeCancel(running);
        for (ObserverRegistration registration : registrations) registration.deactivateAndAwait();
    }

    @Override
    public void close() {
        dispose();
    }

    private void debounceElapsed(long generation) {
        boolean start = false;
        synchronized (this) {
            if (disposed || generation != requestedGeneration) return;
            scheduled = null;
            readyGeneration = generation;
            if (!loading) {
                loading = true;
                loadingGeneration = generation;
                readyGeneration = 0;
                start = true;
            }
        }
        if (start) startLoad(generation);
    }

    private void startLoad(long generation) {
        Load load;
        try {
            load = Objects.requireNonNull(loader.load(generation), "load");
        } catch (RuntimeException exception) {
            loadCompleted(generation, null, exception);
            return;
        }
        CompletionStage<StagedGateView> completion;
        synchronized (this) {
            if (disposed) {
                safeCancel(load);
                return;
            }
            activeLoad = load;
        }
        try {
            completion = Objects.requireNonNull(load.completion(), "completion");
        } catch (RuntimeException exception) {
            loadCompleted(generation, null, exception);
            return;
        }
        completion.whenComplete((view, failure) -> loadCompleted(generation, view, failure));
    }

    private void loadCompleted(long generation, StagedGateView view, Throwable failure) {
        List<ObserverRegistration> publication = List.of();
        boolean startNext = false;
        long nextGeneration = 0;
        synchronized (this) {
            if (disposed || !loading || loadingGeneration != generation) return;
            loading = false;
            activeLoad = null;
            if (failure == null && view != null && generation == requestedGeneration) {
                publication = new ArrayList<>(observers.values());
            }
            if (scheduled == null && readyGeneration == requestedGeneration
                    && readyGeneration > generation) {
                nextGeneration = readyGeneration;
                readyGeneration = 0;
                loading = true;
                loadingGeneration = nextGeneration;
                startNext = true;
            }
        }
        for (ObserverRegistration registration : publication) registration.deliver(view);
        if (startNext) startLoad(nextGeneration);
    }

    private static void safeCancel(ScheduledTask task) {
        if (task == null) return;
        try {
            task.cancel();
        } catch (RuntimeException ignored) {
            // Cancellation is best effort; disposal still suppresses publication.
        }
    }

    private static void safeCancel(Load load) {
        if (load == null) return;
        try {
            load.cancel();
        } catch (RuntimeException ignored) {
            // Cancellation is best effort; disposal still suppresses publication.
        }
    }

    @FunctionalInterface
    public interface Scheduler {
        ScheduledTask schedule(Duration delay, Runnable task);
    }

    @FunctionalInterface
    public interface ScheduledTask {
        void cancel();
    }

    @FunctionalInterface
    public interface Loader {
        Load load(long generation);
    }

    public interface Load {
        CompletionStage<StagedGateView> completion();
        void cancel();
    }

    @FunctionalInterface
    public interface Observer {
        void onGateChanged(StagedGateView view);
    }

    private static final class ObserverRegistration {
        private final Observer observer;
        private boolean active = true;
        private Thread deliveringThread;

        private ObserverRegistration(Observer observer) {
            this.observer = observer;
        }

        private void deliver(StagedGateView view) {
            synchronized (this) {
                if (!active) return;
                deliveringThread = Thread.currentThread();
            }
            try {
                observer.onGateChanged(view);
            } catch (RuntimeException ignored) {
                // An observer cannot break coordinator state or another observer.
            } finally {
                synchronized (this) {
                    deliveringThread = null;
                    notifyAll();
                }
            }
        }

        private void deactivateAndAwait() {
            boolean interrupted = false;
            synchronized (this) {
                active = false;
                while (deliveringThread != null && deliveringThread != Thread.currentThread()) {
                    try {
                        wait();
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
