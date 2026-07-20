package dev.codedefense.domain;

import java.util.Objects;
import java.util.UUID;

public record PassportAttemptId(String value) {
    public PassportAttemptId {
        Objects.requireNonNull(value, "value");
        try {
            if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("attempt ID must use canonical UUID format", exception);
        }
    }
}
