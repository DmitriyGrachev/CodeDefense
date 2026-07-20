package dev.codedefense.application;

import dev.codedefense.domain.PassportAttemptId;

@FunctionalInterface
public interface AttemptIdGenerator {
    PassportAttemptId create();
    static AttemptIdGenerator random() {
        return () -> new PassportAttemptId(java.util.UUID.randomUUID().toString());
    }
}
