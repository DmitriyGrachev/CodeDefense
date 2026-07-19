package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.bridge.BridgeEvent;
import dev.codedefense.bridge.BridgeJsonCodec;
import dev.codedefense.bridge.BridgeProtocol;
import dev.codedefense.domain.CommitSelector;
import dev.codedefense.domain.DefenseFocus;
import dev.codedefense.domain.RangeSelector;
import dev.codedefense.domain.StagedSelector;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class BridgeProveCommandTest {
    @Test
    void delegatesTypedStagedRequestAndEmitsOnlyNdjsonToStdout() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        AtomicReference<Path> path = new AtomicReference<>();
        AtomicReference<DefenseFocus> focus = new AtomicReference<>();
        BridgeProveCommand command = command((repository, selector, selectedFocus, dryRun, session) -> {
            path.set(repository);
            focus.set(selectedFocus);
            assertInstanceOf(StagedSelector.class, selector);
            assertTrue(dryRun);
            session.emit(new BridgeEvent.HelloEvent(1, List.of("interactiveDefenseV1")));
            session.emit(new BridgeEvent.PreviewEvent(1, "demo", "Staged change", "testing", 1, 2, 0));
            session.emit(new BridgeEvent.CompletedEvent(1, 0, false));
            return ExitCodes.SUCCESS;
        }, stdout, stderr);

        int exit = new CommandLine(command).execute("--protocol", "1", "--staged", "--focus", "testing",
                "--dry-run", "project with spaces");

        assertEquals(ExitCodes.SUCCESS, exit);
        assertEquals(Path.of("project with spaces"), path.get());
        assertEquals(DefenseFocus.TESTING, focus.get());
        assertEquals(List.of("HelloEvent", "PreviewEvent", "CompletedEvent"), eventNames(stdout));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void supportsCommitAndRangeAsExclusiveTypedSelectors() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        java.util.ArrayList<Object> selectors = new java.util.ArrayList<>();
        BridgeProveCommand.BridgeDefenseRunner runner = (path, selector, focus, dryRun, session) -> {
            selectors.add(selector);
            session.emit(new BridgeEvent.CompletedEvent(1, 0, false));
            return 0;
        };

        assertEquals(0, new CommandLine(command(runner, output, new ByteArrayOutputStream()))
                .execute("--protocol", "1", "--commit", "HEAD~1", "--dry-run"));
        output.reset();
        assertEquals(0, new CommandLine(command(runner, output, new ByteArrayOutputStream()))
                .execute("--protocol", "1", "--range", "main...HEAD", "--dry-run"));

        assertInstanceOf(CommitSelector.class, selectors.get(0));
        assertInstanceOf(RangeSelector.class, selectors.get(1));
    }

    @Test
    void selectsSupportedProtocolForTheBridgeSession() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicInteger selectedVersion = new AtomicInteger();
        BridgeProveCommand.BridgeDefenseRunner runner = (path, selector, focus, dryRun, session) -> {
            selectedVersion.set(session.protocolVersion());
            session.emit(new BridgeEvent.CompletedEvent(BridgeProtocol.VERSION_1, 0, false));
            return 0;
        };

        int exit = new CommandLine(command(runner, output, new ByteArrayOutputStream()))
                .execute("--protocol", "2", "--staged", "--dry-run");

        assertEquals(0, exit);
        assertEquals(2, selectedVersion.get());
        assertEquals(2, events(output).getFirst().protocolVersion());
    }

    @Test
    void validationFailuresAreStableErrorEventsAndDoNotConstructWorkflow() {
        AtomicInteger calls = new AtomicInteger();
        BridgeProveCommand.BridgeDefenseRunner runner = (path, selector, focus, dryRun, session) -> {
            calls.incrementAndGet();
            return 0;
        };

        assertValidationError(runner, "--protocol", "3", "--staged");
        assertValidationError(runner, "--protocol", "1");
        assertValidationError(runner, "--protocol", "1", "--staged", "--commit", "HEAD");
        assertValidationError(runner, "--protocol", "1", "--staged", "--focus", "free-form");
        assertEquals(0, calls.get());
    }

    @Test
    void helpDoesNotConstructWorkflowOrReadBridgeInput() {
        AtomicInteger calls = new AtomicInteger();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        BridgeProveCommand command = command((path, selector, focus, dryRun, session) -> {
            calls.incrementAndGet();
            return 0;
        }, stdout, new ByteArrayOutputStream());

        assertEquals(0, new CommandLine(command).execute("--help"));
        assertEquals(0, calls.get());
        assertTrue(stdout.toString(StandardCharsets.UTF_8).isEmpty(),
                "Picocli help uses its configured writer, not bridge stdout");
    }

    @Test
    void sensitiveAnswersNeverAppearInDiagnostics() {
        String marker = "PRIVATE-ANSWER-8127";
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        byte[] input = ("{\"protocolVersion\":1,\"type\":\"answer\",\"answer\":\"" + marker + "\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        BridgeProveCommand command = new BridgeProveCommand((path, selector, focus, dryRun, session) -> {
            throw new dev.codedefense.bridge.BridgeProtocolException("Unexpected bridge request.");
        }, () -> new ByteArrayInputStream(input), () -> stdout, () -> stderr);

        assertEquals(ExitCodes.INVALID_USAGE,
                new CommandLine(command).execute("--protocol", "1", "--staged"));
        assertFalse(stdout.toString(StandardCharsets.UTF_8).contains(marker));
        assertFalse(stderr.toString(StandardCharsets.UTF_8).contains(marker));
    }

    private void assertValidationError(BridgeProveCommand.BridgeDefenseRunner runner, String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exit = new CommandLine(command(runner, output, new ByteArrayOutputStream())).execute(args);
        assertEquals(ExitCodes.INVALID_USAGE, exit);
        List<BridgeEvent> events = events(output);
        assertEquals(1, events.size());
        assertEquals("INVALID_REQUEST", ((BridgeEvent.ErrorEvent) events.getFirst()).code());
    }

    private BridgeProveCommand command(BridgeProveCommand.BridgeDefenseRunner runner,
            ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        return new BridgeProveCommand(runner, () -> new ByteArrayInputStream(new byte[0]),
                () -> stdout, () -> stderr);
    }

    private List<String> eventNames(ByteArrayOutputStream output) {
        return events(output).stream().map(event -> event.getClass().getSimpleName()).toList();
    }

    private List<BridgeEvent> events(ByteArrayOutputStream output) {
        BridgeJsonCodec codec = new BridgeJsonCodec();
        return output.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isEmpty())
                .map(line -> codec.decodeEvent((line + "\n").getBytes(StandardCharsets.UTF_8)))
                .toList();
    }
}
