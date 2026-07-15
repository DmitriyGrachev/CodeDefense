package dev.codedefense.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class JdkProcessExecutor implements ProcessExecutor {
    private static final int BUFFER_SIZE = 8 * 1024;

    @Override
    public ProcessResult execute(ProcessSpec spec) {
        Objects.requireNonNull(spec, "Process specification");
        Instant startedAt = Instant.now();
        long deadlineNanos = System.nanoTime() + spec.timeout().toNanos();
        Process process = start(spec);
        BoundedCapture stdout = new BoundedCapture(spec.maximumStdoutBytes());
        BoundedCapture stderr = new BoundedCapture(spec.maximumStderrBytes());
        ProcessTask<Void> stdoutTask = startTask(() -> {
            stdout.drain(process.getInputStream());
            return null;
        });
        ProcessTask<Void> stderrTask = startTask(() -> {
            stderr.drain(process.getErrorStream());
            return null;
        });
        ProcessTask<InputWriteResult> inputTask = startTask(
                () -> writeStandardInput(process, spec.standardInput()));

        try {
            boolean timedOut = !waitForProcess(process, deadlineNanos)
                    || !awaitUntil(inputTask.future(), deadlineNanos, "write standard input")
                    || !awaitUntil(stdoutTask.future(), deadlineNanos, "capture standard output")
                    || !awaitUntil(stderrTask.future(), deadlineNanos, "capture standard error");
            if (timedOut) {
                abort(process, spec.terminationGracePeriod(), inputTask, stdoutTask, stderrTask);
                return result(process, stdout, stderr, true, startedAt);
            }

            InputWriteResult inputWriteResult = inputTask.future().getNow(null);
            if (inputWriteResult.failure() != null) {
                throw new IllegalStateException("Unable to write standard input", inputWriteResult.failure());
            }
            return result(process, stdout, stderr, false, startedAt);
        } catch (InterruptedException exception) {
            abortAfterInterrupt(process, spec.terminationGracePeriod(), inputTask, stdoutTask, stderrTask);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Process execution interrupted", exception);
        } catch (RuntimeException exception) {
            abortAfterFailure(process, spec.terminationGracePeriod(), inputTask, stdoutTask, stderrTask);
            throw exception;
        }
    }

    private static Process start(ProcessSpec spec) {
        ProcessBuilder builder = new ProcessBuilder(spec.command());
        builder.directory(spec.workingDirectory().toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.putAll(spec.environment());
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start process", exception);
        }
    }

    private static boolean waitForProcess(Process process, long deadlineNanos) throws InterruptedException {
        if (!process.isAlive()) {
            return true;
        }
        long remainingNanos = remainingNanos(deadlineNanos);
        return remainingNanos > 0 && process.waitFor(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private static boolean awaitUntil(CompletableFuture<?> task, long deadlineNanos, String operation)
            throws InterruptedException {
        long remainingNanos = remainingNanos(deadlineNanos);
        if (remainingNanos == 0 && !task.isDone()) {
            return false;
        }
        try {
            task.get(remainingNanos, TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException exception) {
            return false;
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to " + operation, exception.getCause());
        }
    }

    private static InputWriteResult writeStandardInput(Process process, String standardInput) {
        try (OutputStream output = process.getOutputStream()) {
            output.write(standardInput.getBytes(StandardCharsets.UTF_8));
            output.flush();
            return new InputWriteResult(null);
        } catch (IOException exception) {
            return new InputWriteResult(exception);
        }
    }

    private static void abort(
            Process process,
            Duration gracePeriod,
            ProcessTask<?> inputTask,
            ProcessTask<?> stdoutTask,
            ProcessTask<?> stderrTask) throws InterruptedException {
        try {
            terminate(process, gracePeriod);
        } finally {
            closeStreams(process);
            inputTask.cancel();
            stdoutTask.cancel();
            stderrTask.cancel();
        }
    }

    private static void terminate(Process process, Duration gracePeriod) throws InterruptedException {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        if (!process.waitFor(gracePeriod.toNanos(), TimeUnit.NANOSECONDS) && process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(gracePeriod.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private static void closeStreams(Process process) {
        closeAsync(process.getOutputStream());
        closeAsync(process.getInputStream());
        closeAsync(process.getErrorStream());
    }

    private static void closeAsync(AutoCloseable closeable) {
        Thread.ofVirtual().start(() -> close(closeable));
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static void abortAfterInterrupt(
            Process process,
            Duration gracePeriod,
            ProcessTask<?> inputTask,
            ProcessTask<?> stdoutTask,
            ProcessTask<?> stderrTask) {
        try {
            abort(process, gracePeriod, inputTask, stdoutTask, stderrTask);
        } catch (InterruptedException ignored) {
        }
    }

    private static void abortAfterFailure(
            Process process,
            Duration gracePeriod,
            ProcessTask<?> inputTask,
            ProcessTask<?> stdoutTask,
            ProcessTask<?> stderrTask) {
        try {
            abort(process, gracePeriod, inputTask, stdoutTask, stderrTask);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static ProcessResult result(
            Process process, BoundedCapture stdout, BoundedCapture stderr, boolean timedOut, Instant startedAt) {
        CapturedOutput capturedStdout = stdout.snapshot();
        CapturedOutput capturedStderr = stderr.snapshot();
        return new ProcessResult(
                process.isAlive() ? -1 : process.exitValue(),
                capturedStdout.text(),
                capturedStderr.text(),
                capturedStdout.truncated(),
                capturedStderr.truncated(),
                timedOut,
                Duration.between(startedAt, Instant.now()));
    }

    private static long remainingNanos(long deadlineNanos) {
        return Math.max(0, deadlineNanos - System.nanoTime());
    }

    private static <T> ProcessTask<T> startTask(Callable<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                future.complete(action.call());
            } catch (Throwable exception) {
                future.completeExceptionally(exception);
            }
        });
        return new ProcessTask<>(future, thread);
    }

    private static final class BoundedCapture {
        private final ByteArrayOutputStream captured;
        private final int limit;
        private boolean truncated;

        private BoundedCapture(int limit) {
            this.captured = new ByteArrayOutputStream(Math.min(limit, BUFFER_SIZE));
            this.limit = limit;
        }

        private void drain(InputStream input) throws IOException {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                append(buffer, read);
            }
        }

        private synchronized void append(byte[] buffer, int read) {
            int remaining = limit - captured.size();
            if (remaining > 0) {
                captured.write(buffer, 0, Math.min(read, remaining));
            }
            if (read > remaining) {
                truncated = true;
            }
        }

        private synchronized CapturedOutput snapshot() {
            return new CapturedOutput(captured.toString(StandardCharsets.UTF_8), truncated);
        }
    }

    private record ProcessTask<T>(CompletableFuture<T> future, Thread thread) {
        private void cancel() {
            future.cancel(true);
            thread.interrupt();
        }
    }

    private record CapturedOutput(String text, boolean truncated) {
    }

    private record InputWriteResult(IOException failure) {
    }
}
