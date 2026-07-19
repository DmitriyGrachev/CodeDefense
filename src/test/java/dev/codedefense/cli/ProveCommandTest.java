package dev.codedefense.cli;

import dev.codedefense.application.StagedChangeDefenseRunner;
import dev.codedefense.application.GitChangeDefenseRunner;
import dev.codedefense.domain.ChangeKind;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProveCommandTest {
    @Test
    void stagedDryRunDelegatesOnceWithConfiguredWriters() {
        AtomicInteger calls = new AtomicInteger();
        StagedChangeDefenseRunner runner = (path, dryRun, yes, out, err) -> {
            calls.incrementAndGet();
            assertEquals(Path.of("repo"), path);
            assertEquals(true, dryRun);
            return ExitCodes.SUCCESS;
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new ProveCommand(runner));
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--staged", "--dry-run", "repo"));
        assertEquals(1, calls.get());
    }

    @Test
    void stagedOptionIsRequired() {
        assertEquals(ExitCodes.INVALID_USAGE, new CommandLine(new ProveCommand((p, d, y, o, e) -> 0)).execute("."));
    }

    @Test
    void defaultPathAndYesAreDelegated() {
        StagedChangeDefenseRunner runner = (path, dryRun, yes, out, err) -> {
            assertEquals(Path.of("."), path);
            assertEquals(true, yes);
            return ExitCodes.SUCCESS;
        };
        assertEquals(ExitCodes.SUCCESS, new CommandLine(new ProveCommand(runner)).execute("--staged", "--yes"));
    }

    @Test
    void configuredPicocliInputAcceptsConfirmationWithoutSystemConsole() {
        AtomicReference<Boolean> accepted = new AtomicReference<>();
        ProveCommand command = new ProveCommand(
                confirmationFactory -> (path, dryRun, yes, out, err) -> {
                    accepted.set(confirmationFactory.get().confirm("ignored"));
                    return ExitCodes.SUCCESS;
                },
                () -> new StringReader("YeS\n"));
        CommandLine commandLine = new CommandLine(command);

        assertEquals(ExitCodes.SUCCESS, commandLine.execute("--staged"));
        assertEquals(true, accepted.get());
    }

    @Test
    void commitAndRangeSelectorsAreExclusiveAndDelegatedAsTypedValues() {
        AtomicReference<ChangeKind> kind = new AtomicReference<>();
        GitChangeDefenseRunner runner = (path, selector, dryRun, yes, out, err) -> {
            kind.set(selector.kind());
            return ExitCodes.SUCCESS;
        };
        CommandLine command = new CommandLine(new ProveCommand(runner));

        assertEquals(ExitCodes.SUCCESS, command.execute("--commit", "HEAD~1", "repo"));
        assertEquals(ChangeKind.COMMIT, kind.get());
        assertEquals(ExitCodes.SUCCESS, command.execute("--range", "main...HEAD", "repo"));
        assertEquals(ChangeKind.RANGE, kind.get());
        assertEquals(ExitCodes.INVALID_USAGE,
                command.execute("--staged", "--commit", "HEAD", "repo"));
    }

    @Test
    void acceptsFourFocusModesAndRejectsFreeFormFocus() {
        GitChangeDefenseRunner runner = (path, selector, dryRun, yes, out, err) -> ExitCodes.SUCCESS;
        CommandLine command = new CommandLine(new ProveCommand(runner));
        for (String focus : new String[] {"balanced", "architecture", "failure-modes", "testing"}) {
            assertEquals(ExitCodes.SUCCESS, command.execute("--staged", "--focus", focus, "--dry-run"));
        }
        assertEquals(ExitCodes.INVALID_USAGE,
                command.execute("--staged", "--focus", "invent-a-prompt", "--dry-run"));
    }
}
