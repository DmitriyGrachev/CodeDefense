package dev.codedefense.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.application.EvidenceCoverageView;
import dev.codedefense.domain.EvidenceCoverageHunk;
import dev.codedefense.domain.EvidenceCoverageMap;
import dev.codedefense.domain.EvidenceCoverageState;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class PassportCoverageCommandTest {
    @Test
    void rendersCurrentLocalDetailsWithoutSourceContent() {
        EvidenceCoverageMap map = new EvidenceCoverageMap("a".repeat(64), List.of(
                new EvidenceCoverageHunk("src/App.java", 1, 10, 10, true,
                        EvidenceCoverageState.REFERENCED, List.of("decision")),
                new EvidenceCoverageHunk("src/App.java", 2, 30, 30, true,
                        EvidenceCoverageState.UNREFERENCED, List.of())));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CommandLine command = new CommandLine(new PassportCoverageCommand(
                path -> new EvidenceCoverageView(Optional.of(map.summary()), Optional.of(map), "CURRENT")));
        command.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        assertEquals(ExitCodes.SUCCESS, command.execute("."));

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Evidence Coverage: 1 / 2 · 50%"));
        assertTrue(text.contains("src/App.java:30 — Not referenced"));
        assertTrue(text.contains("Evidence use only — not correctness or safety coverage."));
        assertFalse(text.contains("private-source"));
    }
}
