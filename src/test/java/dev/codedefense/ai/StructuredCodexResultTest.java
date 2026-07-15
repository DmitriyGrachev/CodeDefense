package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StructuredCodexResultTest {
    @Test
    void acceptsValidStructuredResult() {
        StructuredCodexResult result = new StructuredCodexResult("{\"status\":\"ok\"}", Duration.ofMillis(25),
                "gpt-5.6-terra");

        assertEquals("{\"status\":\"ok\"}", result.finalJson());
        assertEquals(Duration.ofMillis(25), result.duration());
        assertEquals("gpt-5.6-terra", result.model());
        assertFalse(result.toString().contains("status"));
    }

    @Test
    void rejectsInvalidStructuredResult() {
        assertThrows(IllegalArgumentException.class,
                () -> new StructuredCodexResult(" ", Duration.ZERO, "gpt-5.6-terra"));
        assertThrows(IllegalArgumentException.class,
                () -> new StructuredCodexResult("{}", Duration.ofMillis(-1), "gpt-5.6-terra"));
        assertThrows(IllegalArgumentException.class,
                () -> new StructuredCodexResult("{}", Duration.ZERO, " "));
    }
}
