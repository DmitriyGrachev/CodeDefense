package dev.codedefense.jetbrains.gate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import dev.codedefense.jetbrains.settings.CodeDefenseSettings;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Project-owned event-driven staged Passport gate state. */
public final class CodeDefenseProjectGateService implements Disposable {
    private static final StagedGateView UNAVAILABLE = new StagedGateView(1,
            StagedGateView.State.UNAVAILABLE, StagedGateView.Reason.GIT_CAPTURE_FAILED,
            "", 0, 0, 0, 0, java.util.List.of());

    private final StagedGateCoordinator coordinator;
    private final Runnable shutdown;
    private volatile StagedGateView cached;

    public CodeDefenseProjectGateService(Project project) {
        this(runtime(Objects.requireNonNull(project, "project")));
        requestRefresh();
    }

    CodeDefenseProjectGateService(StagedGateCoordinator coordinator, Runnable shutdown) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.shutdown = Objects.requireNonNull(shutdown, "shutdown");
        coordinator.addObserver(view -> cached = view);
    }

    private CodeDefenseProjectGateService(Runtime runtime) {
        this(runtime.coordinator(), runtime.shutdown());
    }

    public static CodeDefenseProjectGateService getInstance(Project project) {
        return project.getService(CodeDefenseProjectGateService.class);
    }

    public Optional<StagedGateView> cached() {
        return Optional.ofNullable(cached);
    }

    public void requestRefresh() {
        coordinator.requestRefresh();
    }

    public void addObserver(StagedGateCoordinator.Observer observer) {
        coordinator.addObserver(observer);
    }

    public void removeObserver(StagedGateCoordinator.Observer observer) {
        coordinator.removeObserver(observer);
    }

    /** Requests a new debounced generation and never substitutes the cached generation. */
    public StagedGateView fresh(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        CountDownLatch completed = new CountDownLatch(1);
        StagedGateView[] result = new StagedGateView[1];
        StagedGateCoordinator.Observer observer = view -> {
            result[0] = view;
            completed.countDown();
        };
        // Keep registration and generation creation atomic against an older load publishing.
        synchronized (coordinator) {
            coordinator.addObserver(observer);
            coordinator.requestRefresh();
        }
        try {
            if (!completed.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) return UNAVAILABLE;
            return result[0] == null ? UNAVAILABLE : result[0];
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return UNAVAILABLE;
        } catch (ArithmeticException exception) {
            return UNAVAILABLE;
        } finally {
            coordinator.removeObserver(observer);
        }
    }

    @Override
    public void dispose() {
        coordinator.dispose();
        shutdown.run();
    }

    public static Path bundledCliPath(URL classResource) {
        if (classResource == null) return null;
        try {
            var connection = classResource.openConnection();
            connection.setUseCaches(false);
            if (!(connection instanceof JarURLConnection jarConnection)) return null;
            Path pluginJar = Path.of(jarConnection.getJarFileURL().toURI()).toAbsolutePath().normalize();
            Path libDirectory = pluginJar.getParent();
            if (libDirectory == null || libDirectory.getFileName() == null
                    || !libDirectory.getFileName().toString().equalsIgnoreCase("lib")) return null;
            Path pluginRoot = libDirectory.getParent();
            return pluginRoot == null ? null : pluginRoot.resolve("cli").resolve("codedefense.jar");
        } catch (Exception exception) {
            return null;
        }
    }

    private static Runtime runtime(Project project) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("codedefense-gate-debounce").factory());
        ExecutorService worker = Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("codedefense-gate-load").factory());
        String basePath = project.getBasePath();
        Path bundled = bundledCliPath(CodeDefenseProjectGateService.class
                .getResource("CodeDefenseProjectGateService.class"));
        Path cli = bundled == null ? null : CodeDefenseSettings.getInstance().resolveCliJar(bundled);
        StagedGateService service = cli == null ? null : StagedGateService.production(cli);
        StagedGateCoordinator coordinator = new StagedGateCoordinator(
                (delay, task) -> {
                    Future<?> future = scheduler.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
                    return () -> future.cancel(false);
                },
                generation -> load(worker, service, basePath));
        return new Runtime(coordinator, () -> {
            worker.shutdownNow();
            scheduler.shutdownNow();
        });
    }

    private static StagedGateCoordinator.Load load(ExecutorService worker,
            StagedGateService service, String basePath) {
        CompletableFuture<StagedGateView> completion = new CompletableFuture<>();
        Future<?> future = worker.submit(() -> {
            try {
                StagedGateView view = service == null || basePath == null
                        ? UNAVAILABLE : service.refresh(Path.of(basePath));
                completion.complete(view);
            } catch (RuntimeException exception) {
                completion.complete(UNAVAILABLE);
            }
        });
        return new StagedGateCoordinator.Load() {
            @Override public CompletableFuture<StagedGateView> completion() { return completion; }
            @Override public void cancel() {
                future.cancel(true);
                completion.cancel(false);
            }
        };
    }

    private record Runtime(StagedGateCoordinator coordinator, Runnable shutdown) { }
}
