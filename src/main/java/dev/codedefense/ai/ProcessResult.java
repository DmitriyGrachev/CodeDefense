package dev.codedefense.ai;

import java.time.Duration;
import java.util.Objects;

public record ProcessResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean stdoutTruncated,
        boolean stderrTruncated,
        boolean timedOut,
        Duration duration) {

    public ProcessResult {
        Objects.requireNonNull(stdout, "Standard output");
        Objects.requireNonNull(stderr, "Standard error");
        Objects.requireNonNull(duration, "Duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative");
        }
    }

    @Override
    public String toString() {
        return "ProcessResult[exitCode=%s, stdoutTruncated=%s, stderrTruncated=%s, timedOut=%s, duration=%s]"
                .formatted(exitCode, stdoutTruncated, stderrTruncated, timedOut, duration);
    }
}
