package dev.codedefense.jetbrains.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.jetbrains.process.BridgeTransportException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassportStatusServiceTest {
    @Test
    void parsesBoundedCurrentPassportStatusFromExactCoreCommand() {
        FakeRunner runner = new FakeRunner(0, "{\"protocolVersion\":1,\"present\":true,"
                + "\"status\":\"CURRENT\",\"changeKind\":\"STAGED\","
                + "\"shortFingerprint\":\"abc123def456\",\"focus\":\"testing\","
                + "\"attemptNumber\":2,\"overallScore\":74,\"readiness\":\"READY\","
                + "\"categories\":[{\"id\":\"architecture\",\"score\":71},"
                + "{\"id\":\"failure-modes\",\"score\":69},{\"id\":\"testing\",\"score\":82}]}\n");
        var service = new PassportStatusService(Path.of("C:/plugin/cli/codedefense.jar"), runner,
                Duration.ofSeconds(5));

        PassportStatusView status = service.refresh(Path.of("C:/project with spaces"));

        assertTrue(status.present());
        assertEquals("CURRENT", status.status());
        assertEquals(74, status.overallScore());
        assertEquals(3, status.categories().size());
        assertEquals(List.of("-jar", Path.of("C:/plugin/cli/codedefense.jar").toAbsolutePath().normalize().toString(),
                "passport", "show", Path.of("C:/project with spaces").toAbsolutePath().normalize().toString(),
                "--format", "json"), runner.arguments);
    }

    @Test
    void representsNoPassportAndRejectsMalformedOrFailedResponses() {
        var empty = new PassportStatusService(Path.of("cli.jar"),
                new FakeRunner(0, "{\"protocolVersion\":1,\"present\":false}\n"), Duration.ofSeconds(1));
        assertFalse(empty.refresh(Path.of(".")).present());

        assertThrows(BridgeTransportException.class, () -> new PassportStatusService(Path.of("cli.jar"),
                new FakeRunner(7, ""), Duration.ofSeconds(1)).refresh(Path.of(".")));
        assertThrows(BridgeTransportException.class, () -> new PassportStatusService(Path.of("cli.jar"),
                new FakeRunner(0, "{\"protocolVersion\":1,\"present\":true,\"unknown\":1}\n"),
                Duration.ofSeconds(1)).refresh(Path.of(".")));
    }

    private static final class FakeRunner implements PassportStatusService.CommandRunner {
        private final PassportStatusService.CommandResult result;
        private List<String> arguments;

        private FakeRunner(int exitCode, String stdout) {
            result = new PassportStatusService.CommandResult(exitCode, stdout.getBytes(StandardCharsets.UTF_8));
        }

        @Override public PassportStatusService.CommandResult run(List<String> command, Path workingDirectory,
                Duration timeout) {
            arguments = command.subList(1, command.size());
            return result;
        }
    }
}
