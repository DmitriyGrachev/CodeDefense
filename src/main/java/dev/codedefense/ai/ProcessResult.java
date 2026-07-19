package dev.codedefense.ai;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public record ProcessResult(
        int exitCode,
        String stdout,
        byte[] stdoutBytes,
        String stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated,
        boolean timedOut,
        Duration duration) {

    public ProcessResult {
        Objects.requireNonNull(stdout, "Standard output");
        Objects.requireNonNull(stdoutBytes, "Standard output bytes");
        stdoutBytes = Arrays.copyOf(stdoutBytes, stdoutBytes.length);
        Objects.requireNonNull(stderr, "Standard error");
        Objects.requireNonNull(duration, "Duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
    }

    public ProcessResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean stdoutTruncated,
            boolean stderrTruncated,
            boolean timedOut,
            Duration duration) {
        this(exitCode, stdout, stdout.getBytes(StandardCharsets.UTF_8), stderr,
                stdoutTruncated, stderrTruncated, timedOut, duration);
    }

    @Override
    public byte[] stdoutBytes() {
        return Arrays.copyOf(stdoutBytes, stdoutBytes.length);
    }

    @Override
    public String toString() {
        return "ProcessResult[exitCode=%s, stdoutTruncated=%s, stderrTruncated=%s, timedOut=%s, duration=%s]"
                .formatted(exitCode, stdoutTruncated, stderrTruncated, timedOut, duration);
    }
}
