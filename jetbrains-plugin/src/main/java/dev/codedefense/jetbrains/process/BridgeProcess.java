package dev.codedefense.jetbrains.process;

import dev.codedefense.jetbrains.process.BridgeLineCodec.BridgeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class BridgeProcess implements AutoCloseable {
    private static final int INVALID_USAGE = 2;

    @FunctionalInterface
    interface ProcessStarter {
        Process start(int protocolVersion) throws IOException;
    }

    private final ProcessStarter processStarter;
    private final Consumer<BridgeMessage> eventConsumer;
    private final Duration terminationGrace;
    private final CompletableFuture<Integer> completion = new CompletableFuture<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean fallbackAttempted = new AtomicBoolean();
    private final Object inputLock = new Object();
    private volatile Attempt current;

    BridgeProcess(Process process, BridgeLineCodec codec, Consumer<BridgeMessage> eventConsumer,
            Duration terminationGrace) {
        this.processStarter = null;
        this.eventConsumer = Objects.requireNonNull(eventConsumer, "eventConsumer");
        this.terminationGrace = positive(terminationGrace);
        startAttempt(new Attempt(Objects.requireNonNull(process, "process"),
                Objects.requireNonNull(codec, "codec"), false));
    }

    BridgeProcess(ProcessStarter processStarter, Consumer<BridgeMessage> eventConsumer,
            Duration terminationGrace) {
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        this.eventConsumer = Objects.requireNonNull(eventConsumer, "eventConsumer");
        this.terminationGrace = positive(terminationGrace);
        try {
            startAttempt(new Attempt(processStarter.start(2), new BridgeLineCodec(2), true));
        } catch (IOException | RuntimeException exception) {
            throw new BridgeTransportException("CodeDefense could not be started.");
        }
    }

    public int protocolVersion() {
        Attempt attempt = current;
        if (attempt == null) throw new BridgeTransportException("CodeDefense bridge is unavailable.");
        return attempt.codec.protocolVersion();
    }

    public void sendConfirm(boolean accepted) {
        sendRequest(codec -> codec.confirmRequest(accepted));
    }

    public void sendAnswer(String answer) {
        sendRequest(codec -> codec.answerRequest(answer));
    }

    public void sendSkip() {
        sendRequest(BridgeLineCodec::skipRequest);
    }

    public void sendProvenanceConsent(String threadId, boolean consent) {
        sendRequest(codec -> codec.provenanceConsentRequest(threadId, consent));
    }

    private void sendRequest(Function<BridgeLineCodec, byte[]> requestFactory) {
        Objects.requireNonNull(requestFactory, "requestFactory");
        synchronized (inputLock) {
            Attempt attempt = current;
            if (attempt == null || cancelled.get()) {
                throw new BridgeTransportException("The bridge request could not be sent.");
            }
            byte[] request = Objects.requireNonNull(requestFactory.apply(attempt.codec), "request");
            if (request.length == 0 || request.length > BridgeLineCodec.MAX_LINE_BYTES) {
                throw new BridgeTransportException("The bridge request could not be sent.");
            }
            attempt.outboundRequest.set(true);
            try {
                OutputStream input = attempt.process.getOutputStream();
                input.write(request);
                input.flush();
            } catch (IOException exception) {
                throw new BridgeTransportException("The bridge request could not be sent.", exception);
            }
        }
    }

    public CompletableFuture<Integer> completion() {
        return completion;
    }

    public void cancel() {
        Attempt attempt;
        synchronized (inputLock) {
            if (!cancelled.compareAndSet(false, true)) return;
            attempt = current;
            if (attempt == null) return;
            try {
                OutputStream input = attempt.process.getOutputStream();
                input.write(attempt.codec.cancelRequest());
                input.flush();
            } catch (IOException ignored) {
                // The child may already have closed stdin.
            }
            try {
                attempt.process.getOutputStream().close();
            } catch (IOException ignored) {
                // The child may already have exited.
            }
        }
        terminateTree(attempt.process);
    }

    private void startAttempt(Attempt attempt) {
        synchronized (inputLock) {
            if (cancelled.get()) {
                terminateTree(attempt.process);
                completion.complete(130);
                return;
            }
            current = attempt;
        }
        Thread stdout = Thread.ofVirtual().name("codedefense-bridge-stdout").start(() -> drainStdout(attempt));
        Thread stderr = Thread.ofVirtual().name("codedefense-bridge-stderr").start(() -> drainStderr(attempt));
        Thread.ofVirtual().name("codedefense-bridge-waiter")
                .start(() -> waitForExit(attempt, stdout, stderr));
    }

    private void drainStdout(Attempt attempt) {
        try (InputStream stdout = attempt.process.getInputStream()) {
            long sessionBytes = 0;
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int next;
            while ((next = stdout.read()) >= 0) {
                sessionBytes++;
                if (sessionBytes > BridgeLineCodec.MAX_SESSION_BYTES) {
                    throw new BridgeTransportException("CodeDefense bridge output exceeded its safe limit.");
                }
                if (next == '\n') {
                    line.write(next);
                    BridgeMessage event = attempt.codec.decodeEvent(line.toByteArray());
                    acceptEvent(attempt, event);
                    line.reset();
                } else {
                    if (line.size() >= BridgeLineCodec.MAX_LINE_BYTES) {
                        throw new BridgeTransportException("CodeDefense bridge output exceeded its safe limit.");
                    }
                    line.write(next);
                }
            }
            if (line.size() != 0) {
                throw new BridgeTransportException("CodeDefense returned an incomplete bridge event.");
            }
        } catch (IOException | RuntimeException exception) {
            fail(attempt, exception);
        }
    }

    private void acceptEvent(Attempt attempt, BridgeMessage event) {
        if (event.protocolVersion() == attempt.codec.protocolVersion()) {
            attempt.validEvents.incrementAndGet();
            eventConsumer.accept(event);
            return;
        }
        if (isLegacyUnsupportedVersion(attempt, event)
                && attempt.legacyUnsupportedVersion.compareAndSet(false, true)) {
            return;
        }
        attempt.validEvents.incrementAndGet();
        throw new BridgeTransportException("CodeDefense returned an event for the wrong protocol version.");
    }

    private boolean isLegacyUnsupportedVersion(Attempt attempt, BridgeMessage event) {
        return attempt.codec.protocolVersion() == 2 && event.protocolVersion() == 1
                && attempt.validEvents.get() == 0 && !attempt.outboundRequest.get()
                && event.type().equals("error")
                && "INVALID_REQUEST".equals(event.text("code"))
                && "Unsupported protocol version.".equals(event.text("message"))
                && event.integer("exitCode") == INVALID_USAGE;
    }

    private void drainStderr(Attempt attempt) {
        try (InputStream stderr = attempt.process.getErrorStream()) {
            byte[] buffer = new byte[8192];
            while (stderr.read(buffer) >= 0) {
                // Diagnostics are intentionally drained and discarded.
            }
        } catch (IOException exception) {
            if (attempt.process.isAlive()) {
                fail(attempt, new BridgeTransportException(
                        "CodeDefense diagnostics could not be drained.", exception));
            }
        }
    }

    private void waitForExit(Attempt attempt, Thread stdout, Thread stderr) {
        try {
            int exitCode = attempt.process.waitFor();
            stdout.join();
            stderr.join();
            synchronized (inputLock) {
                if (completion.isDone() || attempt.failure.get() != null) return;
                if (shouldFallback(attempt, exitCode)) {
                    startFallback();
                } else {
                    completion.complete(exitCode);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail(attempt, new BridgeTransportException("CodeDefense bridge execution was interrupted."));
        }
    }

    private boolean shouldFallback(Attempt attempt, int exitCode) {
        return processStarter != null && attempt.fallbackEligible && exitCode == INVALID_USAGE
                && attempt.validEvents.get() == 0 && !attempt.outboundRequest.get() && !cancelled.get()
                && fallbackAttempted.compareAndSet(false, true);
    }

    private void startFallback() {
        try {
            startAttempt(new Attempt(processStarter.start(1), new BridgeLineCodec(1), false));
        } catch (IOException | RuntimeException exception) {
            completion.completeExceptionally(new BridgeTransportException(
                    "CodeDefense could not be started."));
        }
    }

    private void fail(Attempt attempt, Throwable exception) {
        BridgeTransportException safe = exception instanceof BridgeTransportException bridge
                ? bridge : new BridgeTransportException("CodeDefense bridge transport failed.", exception);
        if (attempt.failure.compareAndSet(null, safe)) {
            completion.completeExceptionally(safe);
            terminateTree(attempt.process);
        }
    }

    private void terminateTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(terminationGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(terminationGrace.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private static Duration positive(Duration value) {
        Objects.requireNonNull(value, "terminationGrace");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("terminationGrace must be positive");
        }
        return value;
    }

    @Override
    public void close() {
        cancel();
    }

    private static final class Attempt {
        private final Process process;
        private final BridgeLineCodec codec;
        private final boolean fallbackEligible;
        private final AtomicInteger validEvents = new AtomicInteger();
        private final AtomicBoolean outboundRequest = new AtomicBoolean();
        private final AtomicBoolean legacyUnsupportedVersion = new AtomicBoolean();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private Attempt(Process process, BridgeLineCodec codec, boolean fallbackEligible) {
            this.process = Objects.requireNonNull(process, "process");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.fallbackEligible = fallbackEligible;
        }
    }
}
