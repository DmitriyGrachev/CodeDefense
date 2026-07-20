package dev.codedefense.ci;

import java.util.Objects;

public record PassportTrailer(State state, String fingerprint) {
    public enum State { MISSING, VALID, MALFORMED }

    public PassportTrailer {
        Objects.requireNonNull(state, "state");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        if (state == State.VALID && !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint is invalid");
        }
        if (state != State.VALID && !fingerprint.isEmpty()) {
            throw new IllegalArgumentException("non-valid trailer cannot contain a fingerprint");
        }
    }

    static PassportTrailer missing() { return new PassportTrailer(State.MISSING, ""); }
    static PassportTrailer malformed() { return new PassportTrailer(State.MALFORMED, ""); }
    static PassportTrailer valid(String fingerprint) { return new PassportTrailer(State.VALID, fingerprint); }
}
