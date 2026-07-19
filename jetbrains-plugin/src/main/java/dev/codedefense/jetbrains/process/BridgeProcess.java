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
import java.util.function.Consumer;

public final class BridgeProcess implements AutoCloseable {
    private final Process process;
    private final BridgeLineCodec codec;
    private final Consumer<BridgeMessage> eventConsumer;
    private final Duration terminationGrace;
    private final CompletableFuture<Integer> completion = new CompletableFuture<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Object inputLock = new Object();

    BridgeProcess(Process process, BridgeLineCodec codec, Consumer<BridgeMessage> eventConsumer,
            Duration terminationGrace) {
        this.process = Objects.requireNonNull(process, "process");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.eventConsumer = Objects.requireNonNull(eventConsumer, "eventConsumer");
        this.terminationGrace = Objects.requireNonNull(terminationGrace, "terminationGrace");
        if (terminationGrace.isZero() || terminationGrace.isNegative()) {
            throw new IllegalArgumentException("terminationGrace must be positive");
        }
        Thread stdout = Thread.ofVirtual().name("codedefense-bridge-stdout").start(this::drainStdout);
        Thread stderr = Thread.ofVirtual().name("codedefense-bridge-stderr").start(this::drainStderr);
        Thread.ofVirtual().name("codedefense-bridge-waiter").start(() -> waitForExit(stdout, stderr));
    }

    public void send(byte[] request) {
        Objects.requireNonNull(request, "request");
        if (request.length == 0 || request.length > BridgeLineCodec.MAX_LINE_BYTES || cancelled.get()) {
            throw new BridgeTransportException("The bridge request could not be sent.");
        }
        synchronized (inputLock) {
            try {
                OutputStream input = process.getOutputStream();
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
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        synchronized (inputLock) {
            try {
                OutputStream input = process.getOutputStream();
                input.write(codec.cancelRequest());
                input.flush();
            } catch (IOException ignored) {
                // The child may already have closed stdin.
            }
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // The child may already have exited.
            }
        }
        terminateTree();
    }

    private void drainStdout() {
        try (InputStream stdout = process.getInputStream()) {
            long sessionBytes = 0;
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int current;
            while ((current = stdout.read()) >= 0) {
                sessionBytes++;
                if (sessionBytes > BridgeLineCodec.MAX_SESSION_BYTES) {
                    throw new BridgeTransportException("CodeDefense bridge output exceeded its safe limit.");
                }
                if (current == '\n') {
                    line.write(current);
                    eventConsumer.accept(codec.decodeEvent(line.toByteArray()));
                    line.reset();
                } else {
                    if (line.size() >= BridgeLineCodec.MAX_LINE_BYTES) {
                        throw new BridgeTransportException("CodeDefense bridge output exceeded its safe limit.");
                    }
                    line.write(current);
                }
            }
            if (line.size() != 0) {
                throw new BridgeTransportException("CodeDefense returned an incomplete bridge event.");
            }
        } catch (IOException | RuntimeException exception) {
            fail(exception);
        }
    }

    private void drainStderr() {
        try (InputStream stderr = process.getErrorStream()) {
            byte[] buffer = new byte[8192];
            while (stderr.read(buffer) >= 0) {
                // Diagnostics are intentionally drained and discarded.
            }
        } catch (IOException exception) {
            if (process.isAlive()) {
                fail(new BridgeTransportException("CodeDefense diagnostics could not be drained.", exception));
            }
        }
    }

    private void waitForExit(Thread stdout, Thread stderr) {
        try {
            int exitCode = process.waitFor();
            stdout.join();
            stderr.join();
            completion.complete(exitCode);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail(new BridgeTransportException("CodeDefense bridge execution was interrupted."));
        }
    }

    private void fail(Throwable exception) {
        completion.completeExceptionally(exception instanceof BridgeTransportException
                ? exception : new BridgeTransportException("CodeDefense bridge transport failed.", exception));
        terminateTree();
    }

    private void terminateTree() {
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

    @Override
    public void close() {
        cancel();
    }
}
