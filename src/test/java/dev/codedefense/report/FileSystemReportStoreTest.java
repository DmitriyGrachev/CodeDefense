package dev.codedefense.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.codedefense.domain.FinalReport;
import dev.codedefense.domain.NarrativeSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemReportStoreTest {
    @TempDir Path home;

    @Test
    void savesUtf8MarkdownAndReadsItBackThroughLatestPointer() throws IOException {
        FileSystemReportStore store = store(Instant.parse("2026-07-17T12:34:56.789Z"));
        FinalReport report = report("Unicode π headline");

        Path saved = store.save(report).path();

        assertEquals(home.resolve(".codedefense/reports/20260717-123456-789-project-name.md").toAbsolutePath(), saved);
        assertTrue(Files.readString(saved, StandardCharsets.UTF_8).startsWith("# CodeDefense Understanding Report\n"));
        assertEquals(new MarkdownReportRenderer().render(report), store.readLatest().orElseThrow());
        assertEquals(saved + "\n", Files.readString(home.resolve(".codedefense/latest-report.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void addsCollisionSuffixAndUpdatesLatestPointer() {
        FileSystemReportStore store = store(Instant.EPOCH);
        Path first = store.save(report("First")).path();
        Path second = store.save(report("Second")).path();

        assertTrue(second.getFileName().toString().endsWith("-2.md"));
        assertEquals(second + "\n", read(home.resolve(".codedefense/latest-report.txt")));
        assertFalse(first.equals(second));
    }

    @Test
    void makesAConfiguredSafeProjectSlug() {
        FinalReport report = report("Headline", "  CON <unsafe> project name with a very long suffix ".repeat(5));
        Path saved = store(Instant.EPOCH, 16 * 1024 * 1024, 16).save(report).path();

        assertTrue(saved.getFileName().toString().matches("19700101-000000-000-[a-z0-9-]{1,16}\\.md"));
    }

    @Test
    void rejectsRenderedReportOverConfiguredLimitWithoutCreatingFiles() {
        FileSystemReportStore store = store(Instant.EPOCH, 64 * 1024, 60);
        FinalReport oversized = reportWithAnswer("x".repeat(70_000));
        assertThrows(ReportPersistenceException.class, () -> store.save(oversized));
        assertFalse(Files.exists(home.resolve(".codedefense")));
    }

    @Test
    void fallsBackWhenAtomicMoveIsUnsupportedAndCleansTemporaryFiles() throws IOException {
        FileSystemReportStore store = new FileSystemReportStore(paths(), ReportConfig.defaults(), new MarkdownReportRenderer(), Clock.systemUTC()) {
            @Override void move(Path source, Path target, CopyOption... options) throws IOException {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test");
            }
        };

        store.save(report("Headline"));

        assertTrue(store.readLatest().isPresent());
        try (var entries = Files.list(paths().reportsDirectory())) {
            assertTrue(entries.allMatch(path -> path.getFileName().toString().endsWith(".md")));
        }
    }

    @Test
    void fallsBackWhenAtomicPointerMoveIsUnsupported() throws IOException {
        FileSystemReportStore store = new FileSystemReportStore(paths(), ReportConfig.defaults(), new MarkdownReportRenderer(), Clock.systemUTC()) {
            @Override void move(Path source, Path target, CopyOption... options) throws IOException {
                if (target.equals(paths().latestPointer())) {
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test");
                }
                super.move(source, target, options);
            }
        };

        Path saved = store.save(report("Headline")).path();

        assertEquals(saved + "\n", read(paths().latestPointer()));
        assertTrue(store.readLatest().isPresent());
    }

    @Test
    void cleansOwnedTempsAndPublishedReportWhenPublicationFails() throws IOException {
        FileSystemReportStore store = new FileSystemReportStore(paths(), ReportConfig.defaults(), new MarkdownReportRenderer(), Clock.systemUTC()) {
            private int moves;
            @Override void move(Path source, Path target, CopyOption... options) throws IOException {
                if (++moves == 2) throw new IOException("private filesystem failure");
                super.move(source, target, options);
            }
        };

        ReportPersistenceException failure = assertThrows(ReportPersistenceException.class, () -> store.save(report("Headline")));

        assertEquals(ReportPersistenceException.SAVE_FAILURE_MESSAGE, failure.getMessage());
        assertFalse(Files.exists(paths().latestPointer()));
        try (var reports = Files.list(paths().reportsDirectory())) {
            assertTrue(reports.noneMatch(path -> path.getFileName().toString().endsWith(".md") || path.getFileName().toString().endsWith(".tmp")));
        }
        try (var root = Files.list(paths().rootDirectory())) {
            assertTrue(root.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void removesOwnedOrphanTempsBeforeSaving() throws IOException {
        Files.createDirectories(paths().reportsDirectory());
        Path reportOrphan = Files.writeString(paths().reportsDirectory().resolve(".report-orphan.tmp"), "orphan");
        Path pointerOrphan = Files.writeString(paths().rootDirectory().resolve(".latest-orphan.tmp"), "orphan");

        store(Instant.EPOCH).save(report("Headline"));

        assertFalse(Files.exists(reportOrphan));
        assertFalse(Files.exists(pointerOrphan));
    }

    @Test
    void usesExactSafeSlugRules() {
        assertEquals("19700101-000000-000-hello-world.md", store(Instant.EPOCH).save(report("Headline", " Hello, WORLD!!! ")).path().getFileName().toString());
        assertEquals("19700101-000000-000-project.md", store(Instant.EPOCH).save(report("Headline", "Ж中")).path().getFileName().toString());
        assertEquals("19700101-000000-000-123456789012345.md", store(Instant.EPOCH, 1024 * 1024, 16).save(report("Headline", "123456789012345-foo")).path().getFileName().toString());
    }

    @Test
    void prefixesWindowsReservedProjectSlug() {
        assertEquals("19700101-000000-000-project-con.md", store(Instant.EPOCH).save(report("Headline", "CON")).path().getFileName().toString());
        assertEquals("19700101-000000-000-project-aux.md", store(Instant.EPOCH).save(report("Headline", "aux")).path().getFileName().toString());
    }

    @Test
    void readLatestRejectsUnsafePointerAndReportInputsWithSafeErrors() throws IOException {
        Path pointer = paths().latestPointer();
        Files.createDirectories(pointer.getParent());
        for (byte[] input : new byte[][] {
                "relative.md\n".getBytes(StandardCharsets.UTF_8),
                "../escape.md\n".getBytes(StandardCharsets.UTF_8),
                (home.resolve(".codedefense/reports/missing.md") + "\nextra\n").getBytes(StandardCharsets.UTF_8),
                {(byte) 0xc3, (byte) 0x28}
        }) {
            Files.write(pointer, input);
            ReportPersistenceException failure = assertThrows(ReportPersistenceException.class, () -> store(Instant.EPOCH).readLatest());
            assertEquals(ReportPersistenceException.READ_FAILURE_MESSAGE, failure.getMessage());
        }
        Files.writeString(pointer, paths().reportsDirectory().toAbsolutePath() + "\n", StandardCharsets.UTF_8);
        assertReadFailure();
    }

    @Test
    void readLatestRejectsMalformedUtf8AndOversizeReportAndPointer() throws IOException {
        Path reports = paths().reportsDirectory();
        Files.createDirectories(reports);
        Path report = reports.resolve("valid.md");
        Files.write(report, new byte[] {(byte) 0xc3, (byte) 0x28});
        Files.writeString(paths().latestPointer(), report.toAbsolutePath() + "\n", StandardCharsets.UTF_8);
        assertReadFailure();
        Files.write(report, new byte[64 * 1024 + 1]);
        ReportPersistenceException oversizedReport = assertThrows(ReportPersistenceException.class, () -> store(Instant.EPOCH, 64 * 1024, 60).readLatest());
        assertEquals(ReportPersistenceException.READ_FAILURE_MESSAGE, oversizedReport.getMessage());
        Files.write(paths().latestPointer(), new byte[4097]);
        assertReadFailure();
    }

    @Test
    void rejectsSymlinkPointerOrReportWhenSupported() throws IOException {
        Path outside = home.resolve("outside.txt");
        Files.writeString(outside, "outside");
        Files.createDirectories(paths().rootDirectory());
        try {
            Files.createSymbolicLink(paths().latestPointer(), outside);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }
        assertThrows(ReportPersistenceException.class, () -> store(Instant.EPOCH).readLatest());
    }

    @Test
    void rejectsMissingDirectoryAndSymlinkReportTargetsWithSafeErrors() throws IOException {
        Path reports = paths().reportsDirectory();
        Files.createDirectories(reports);
        Path target = reports.resolve("target.md");
        Files.writeString(paths().latestPointer(), target.toAbsolutePath() + "\n", StandardCharsets.UTF_8);
        assertReadFailure();
        Files.createDirectory(target);
        assertReadFailure();
        Files.delete(target);
        Path outside = home.resolve("outside.md");
        Files.writeString(outside, "outside");
        try {
            Files.createSymbolicLink(target, outside);
        } catch (UnsupportedOperationException | IOException exception) {
            return;
        }
        assertReadFailure();
    }

    @Test
    void constructorDoesNotCreateStorageDirectories() {
        new FileSystemReportStore(paths(), ReportConfig.defaults(), new MarkdownReportRenderer(), Clock.systemUTC());
        assertFalse(Files.exists(paths().rootDirectory()));
    }

    private FileSystemReportStore store(Instant now) { return store(now, 1024 * 1024, 60); }
    private FileSystemReportStore store(Instant now, int reportBytes, int slugLength) {
        return new FileSystemReportStore(paths(), new ReportConfig(ReportConfig.DEFAULT_NARRATIVE_TIMEOUT, reportBytes, 4096, slugLength), new MarkdownReportRenderer(), Clock.fixed(now, ZoneOffset.UTC));
    }
    private CodeDefensePaths paths() { return CodeDefensePaths.under(home); }
    private FinalReport report(String headline) { return report(headline, "project-name"); }
    private FinalReport report(String headline, String projectName) {
        var request = Fixtures.request();
        var metadata = new dev.codedefense.domain.ReportMetadata(Instant.EPOCH, "model", projectName, request.analysis().projectType(), java.util.List.of(new dev.codedefense.domain.AnalyzedFile("src/App.java", 1, false, 1)), 1, 0);
        var analysis = new dev.codedefense.domain.ProjectAnalysis(projectName, request.analysis().projectType(), request.analysis().summary(), request.analysis().mainFlow(), request.analysis().components(), request.analysis().criticalTopics(), request.analysis().questions());
        var session = new dev.codedefense.domain.InterviewSession(projectName, request.session().results(), request.session().overallScore(), request.session().readiness(), request.session().skippedQuestionCount());
        return new FinalReport(metadata, analysis, session, new dev.codedefense.domain.ReportNarrative(headline, Fixtures.narrative().summary(), Fixtures.narrative().strengths(), Fixtures.narrative().knowledgeGaps(), Fixtures.narrative().recommendedActions()), NarrativeSource.DETERMINISTIC_FALLBACK);
    }
    private FinalReport reportWithAnswer(String answer) {
        var request = Fixtures.request("Question prompt", answer, "key", "reason");
        var metadata = new dev.codedefense.domain.ReportMetadata(Instant.EPOCH, "model", request.analysis().projectName(), request.analysis().projectType(), java.util.List.of(new dev.codedefense.domain.AnalyzedFile("src/App.java", 1, false, 1)), 1, 0);
        return new FinalReport(metadata, request.analysis(), request.session(), Fixtures.narrative(), NarrativeSource.DETERMINISTIC_FALLBACK);
    }
    private static String read(Path path) { try { return Files.readString(path, StandardCharsets.UTF_8); } catch (IOException exception) { throw new AssertionError(exception); } }
    private void assertReadFailure() {
        ReportPersistenceException failure = assertThrows(ReportPersistenceException.class, () -> store(Instant.EPOCH).readLatest());
        assertEquals(ReportPersistenceException.READ_FAILURE_MESSAGE, failure.getMessage());
    }
}
