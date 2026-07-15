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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class JdkProcessExecutor implements ProcessExecutor {
    private static final int BUFFER_SIZE = 8 * 1024;

    @Override
    public ProcessResult execute(ProcessSpec spec) {
        Objects.requireNonNull(spec, "Process specification");
        Instant startedAt = Instant.now();
        Process process = start(spec);

        try (ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<CapturedOutput> stdout = tasks.submit(
                    () -> capture(process.getInputStream(), spec.maximumStdoutBytes()));
            Future<CapturedOutput> stderr = tasks.submit(
                    () -> capture(process.getErrorStream(), spec.maximumStderrBytes()));
            Future<Void> standardInput = tasks.submit(() -> {
                writeStandardInput(process, spec.standardInput());
                return null;
            });

            if (!waitFor(process, spec.timeout())) {
                terminate(process, spec.terminationGracePeriod());
            }

            await(standardInput, process, spec.terminationGracePeriod(), "write standard input");
            CapturedOutput capturedStdout = await(
                    stdout, process, spec.terminationGracePeriod(), "capture standard output");
            CapturedOutput capturedStderr = await(
                    stderr, process, spec.terminationGracePeriod(), "capture standard error");
            return new ProcessResult(
                    process.exitValue(),
                    capturedStdout.text(),
                    capturedStderr.text(),
                    capturedStdout.truncated(),
                    capturedStderr.truncated(),
                    Duration.between(startedAt, Instant.now()));
        } catch (RuntimeException exception) {
            terminateIfAlive(process, spec.terminationGracePeriod());
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

    private static boolean waitFor(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            terminateIfAlive(process, Duration.ZERO);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Process execution interrupted", exception);
        }
    }

    private static void writeStandardInput(Process process, String standardInput) throws IOException {
        try (OutputStream output = process.getOutputStream()) {
            output.write(standardInput.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    private static CapturedOutput capture(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(limit, BUFFER_SIZE));
        byte[] buffer = new byte[BUFFER_SIZE];
        boolean truncated = false;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int remaining = limit - captured.size();
            if (remaining > 0) {
                captured.write(buffer, 0, Math.min(read, remaining));
            }
            if (read > remaining) {
                truncated = true;
            }
        }
        return new CapturedOutput(captured.toString(StandardCharsets.UTF_8), truncated);
    }

    private static <T> T await(
            Future<T> future, Process process, Duration terminationGracePeriod, String operation) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            terminateIfAlive(process, terminationGracePeriod);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Process execution interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to " + operation, exception.getCause());
        }
    }

    private static void terminateIfAlive(Process process, Duration gracePeriod) {
        if (!process.isAlive()) {
            return;
        }
        terminate(process, gracePeriod);
    }

    private static void terminate(Process process, Duration gracePeriod) {
        process.destroy();
        try {
            if (!process.waitFor(gracePeriod.toNanos(), TimeUnit.NANOSECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Process execution interrupted", exception);
        }
    }

    private record CapturedOutput(String text, boolean truncated) {
    }
}
