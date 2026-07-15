package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.ai.exception.CodexException;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import org.junit.jupiter.api.Test;

class CodexExceptionTest {
    @Test
    void readinessExceptionsUseSafeFixedMessages() {
        CodexNotInstalledException missing = new CodexNotInstalledException();
        CodexNotAuthenticatedException unauthenticated = new CodexNotAuthenticatedException();

        assertInstanceOf(CodexException.class, missing);
        assertTrue(missing.getMessage().contains("Codex CLI was not found"));
        assertTrue(unauthenticated.getMessage().contains("not authenticated"));
    }

    @Test
    void executionExceptionExposesExitCodeAndNormalizesDiagnosticLines() {
        CodexExecutionException exception = new CodexExecutionException(17, "first\r\nsecond\rthird");

        assertEquals(17, exception.exitCode());
        assertTrue(exception.getMessage().contains("first\nsecond\nthird"));
    }

    @Test
    void remainingFailuresShareTheCodexExceptionHierarchy() {
        InterruptedException cause = new InterruptedException("interrupted");
        CodexInterruptedException interrupted = new CodexInterruptedException(cause);

        assertInstanceOf(CodexException.class, new CodexTimeoutException());
        assertInstanceOf(CodexException.class, interrupted);
        assertInstanceOf(CodexException.class, new InvalidCodexResponseException("Invalid final response"));
        assertSame(cause, interrupted.getCause());
    }
}
