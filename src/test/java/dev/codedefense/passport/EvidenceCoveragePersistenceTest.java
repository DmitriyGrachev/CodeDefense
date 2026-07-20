package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.EvidenceCoverageHunk;
import dev.codedefense.domain.EvidenceCoverageMap;
import dev.codedefense.domain.EvidenceCoverageState;
import dev.codedefense.domain.PassportStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceCoveragePersistenceTest {
    @TempDir Path directory;

    @Test
    void storesAggregateInSchemaFiveAndBoundedDetailsLocally() throws Exception {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), new PassportReceiptJsonCodec(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                () -> "7bd53719-1de8-4c78-a48a-430aa38555dc");

        Path markdown = store.save(withCoverage());

        StoredChangePassport latest = store.readLatest().orElseThrow();
        assertEquals(5, latest.receipt().schemaVersion());
        assertEquals(coverage().summary(), latest.receipt().evidenceCoverage().orElseThrow());
        assertEquals(coverage(), store.readLatestCoverage().orElseThrow().coverage());
        Path sidecar = markdown.resolveSibling(markdown.getFileName().toString()
                .replace(".md", ".coverage.json"));
        String json = Files.readString(sidecar);
        assertTrue(json.contains("src/App.java"));
        for (String forbidden : List.of("private-answer", "feedback", "prompt", "evidence-reason", "+changed")) {
            assertFalse(json.contains(forbidden), forbidden);
        }
    }

    @Test
    void unsafeCoverageSymlinkDegradesToUnavailableWhenSupported() throws Exception {
        ChangePassportPaths paths = ChangePassportPaths.under(directory);
        FileSystemChangePassportStore store = new FileSystemChangePassportStore(paths,
                new MarkdownChangePassportRenderer(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        Path markdown = store.save(withCoverage());
        Path sidecar = markdown.resolveSibling(markdown.getFileName().toString()
                .replace(".md", ".coverage.json"));
        Path outside = directory.resolve("outside.json");
        Files.writeString(outside, "{}\n");
        Files.delete(sidecar);
        try {
            Files.createSymbolicLink(sidecar, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            assumeTrue(false, "Symbolic links are unavailable");
        }

        assertTrue(store.readLatestCoverage().isEmpty());
    }

    private static ChangePassport withCoverage() {
        ChangePassport value = PassportTestFixtures.passport(PassportStatus.CURRENT);
        return new ChangePassport(value.change(), value.changeKind(), value.sourceIdentity(), value.analysis(),
                value.session(), value.createdAt(), value.model(), value.statusAtCreation(), value.focus(),
                value.codexProvenance(), Optional.of(coverage()));
    }

    private static EvidenceCoverageMap coverage() {
        return new EvidenceCoverageMap("d".repeat(64), List.of(
                new EvidenceCoverageHunk("src/App.java", 1, 10, 10, true,
                        EvidenceCoverageState.REFERENCED, List.of("decision")),
                new EvidenceCoverageHunk("src/App.java", 2, 30, 30, true,
                        EvidenceCoverageState.UNREFERENCED, List.of())));
    }
}
