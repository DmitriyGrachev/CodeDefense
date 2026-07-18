package dev.codedefense.sample;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.application.CodeDefenseRuntime;
import dev.codedefense.application.CodeDefenseRuntimeProvider;
import dev.codedefense.application.DefaultProjectDefenseRunner;
import dev.codedefense.application.ProjectDefenseRunner;
import dev.codedefense.application.RunSampleUseCase;
import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.InterviewTurn;
import dev.codedefense.domain.NarrativeSource;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.QuestionResult;
import dev.codedefense.domain.Readiness;
import dev.codedefense.domain.SavedReport;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.domain.TurnType;
import dev.codedefense.domain.Verdict;
import dev.codedefense.interview.InterviewRunner;
import dev.codedefense.report.ReportService;
import dev.codedefense.scanner.FileSystemProjectScanner;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.terminal.ProjectAnalysisRenderer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleWorkflowEndToEndTest {
    @TempDir
    Path temporaryRoot;

    @Test
    void dryRunUsesTheProductionArchiveAndPipelineWithoutRuntimeOrLeakingWorkspace() throws Exception {
        CountingRuntimeProvider runtimeProvider = new CountingRuntimeProvider();
        ProjectDefenseRunner defenseRunner = new DefaultProjectDefenseRunner(
                new FileSystemProjectScanner(),
                new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()),
                prompt -> {
                    throw new AssertionError("Dry-run must not ask for confirmation");
                },
                new ProjectAnalysisRenderer(), runtimeProvider);
        SampleProjectExtractor extractor = new SampleProjectExtractor(
                SampleProjectConfig.defaults(), this::openProductionArchive,
                prefix -> Files.createTempDirectory(temporaryRoot, prefix),
                SampleProjectExtractor::deleteTree);
        RunSampleUseCase useCase = new RunSampleUseCase(extractor, defenseRunner);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS, useCase.run(true, false, writer(output), writer(new ByteArrayOutputStream())));

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Mode: Embedded sample"));
        assertTrue(text.contains("Preparing built-in sample project..."));
        assertTrue(text.contains("Project: codedefense-sample-news-service"));
        assertTrue(text.contains("Detected type: Java / Spring Boot"));
        assertTrue(text.contains("Selected files: 15 / 30"));
        assertTrue(text.contains("No source content was sent."));
        assertTrue(text.contains("Codex was not invoked."));
        assertEquals(0, runtimeProvider.calls);
        try (var children = Files.list(temporaryRoot)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith("codedefense-sample-")));
        }
    }

    @Test
    void normalWorkflowUsesProductionDiscoveryAndFakesForAnalysisInterviewAndReport() throws Exception {
        ProjectAnalysis analysis = analysis();
        InterviewSession session = session(analysis);
        CapturingReportService reports = new CapturingReportService(
                new SavedReport(temporaryRoot.resolve("report.md"), NarrativeSource.AI));
        CapturingAnalyzer analyzer = new CapturingAnalyzer(analysis);
        CapturingRuntimeProvider runtimeProvider = new CapturingRuntimeProvider(
                new CodeDefenseRuntime(analyzer, ignored -> session, reports));
        ProjectDefenseRunner defenseRunner = new DefaultProjectDefenseRunner(
                new FileSystemProjectScanner(),
                new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), prompt -> true,
                new ProjectAnalysisRenderer(), runtimeProvider);
        SampleProjectExtractor extractor = new SampleProjectExtractor(
                SampleProjectConfig.defaults(), this::openProductionArchive,
                prefix -> Files.createTempDirectory(temporaryRoot, prefix),
                SampleProjectExtractor::deleteTree);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertEquals(ExitCodes.SUCCESS,
                new RunSampleUseCase(extractor, defenseRunner).run(false, false, writer(output), writer(new ByteArrayOutputStream())));

        assertEquals(1, runtimeProvider.calls);
        assertEquals(1, analyzer.calls);
        assertEquals(1, reports.calls);
        assertTrue(analyzer.snapshot.scanSummary().acceptedCandidateCount() == 15);
        assertEquals("codedefense-sample-news-service", analyzer.snapshot.projectName());
        assertTrue(analyzer.snapshot.selectedFiles().stream().map(file -> file.relativePath().toString().replace('\\', '/')).toList()
                .containsAll(List.of("README.md", "pom.xml",
                        "src/main/java/com/codedefense/sample/news/ArticleApplication.java",
                        "src/main/java/com/codedefense/sample/news/ArticleScheduler.java",
                        "src/main/java/com/codedefense/sample/news/ArticleService.java",
                        "src/main/java/com/codedefense/sample/news/RetryingArticleProcessor.java")));
        assertEquals(3, session.results().size());
        assertTrue(reports.snapshot == analyzer.snapshot);
        assertTrue(reports.analysis == analysis);
        assertTrue(reports.session == session);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Understanding report saved: "
                + temporaryRoot.resolve("report.md")));
        try (var children = Files.list(temporaryRoot)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith("codedefense-sample-")));
        }
    }

    private InputStream openProductionArchive(String resourcePath) {
        return SampleWorkflowEndToEndTest.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    private static PrintWriter writer(ByteArrayOutputStream output) {
        return new PrintWriter(output, true, StandardCharsets.UTF_8);
    }

    private static final class CountingRuntimeProvider implements CodeDefenseRuntimeProvider {
        private int calls;

        @Override
        public CodeDefenseRuntime create(PrintWriter output) {
            calls++;
            throw new AssertionError("Dry-run must not create JLine, Codex, analysis, interview, or report services");
        }
    }

    private static final class CapturingRuntimeProvider implements CodeDefenseRuntimeProvider {
        private final CodeDefenseRuntime runtime;
        private int calls;

        private CapturingRuntimeProvider(CodeDefenseRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public CodeDefenseRuntime create(PrintWriter output) {
            calls++;
            return runtime;
        }
    }

    private static final class CapturingAnalyzer implements dev.codedefense.analysis.ProjectAnalyzer {
        private final ProjectAnalysis result;
        private int calls;
        private dev.codedefense.domain.ProjectSnapshot snapshot;

        private CapturingAnalyzer(ProjectAnalysis result) {
            this.result = result;
        }

        @Override
        public ProjectAnalysis analyze(dev.codedefense.domain.ProjectSnapshot snapshot) {
            calls++;
            this.snapshot = snapshot;
            return result;
        }
    }

    private static final class CapturingReportService implements ReportService {
        private final SavedReport result;
        private int calls;
        private dev.codedefense.domain.ProjectSnapshot snapshot;
        private ProjectAnalysis analysis;
        private InterviewSession session;

        private CapturingReportService(SavedReport result) {
            this.result = result;
        }

        @Override
        public SavedReport generateAndSave(dev.codedefense.domain.ProjectSnapshot snapshot,
                ProjectAnalysis analysis, InterviewSession session) {
            calls++;
            this.snapshot = snapshot;
            this.analysis = analysis;
            this.session = session;
            return result;
        }
    }

    private static ProjectAnalysis analysis() {
        return new ProjectAnalysis("Sample", "Java / Spring Boot", "Sample analysis.",
                List.of("Polls a feed.", "Stores an article."),
                List.of(new ProjectComponent("Scheduler", "component", "Polls the feed.",
                        List.of("src/main/java/com/codedefense/sample/news/ArticleScheduler.java"))),
                List.of("retries", "persistence"),
                List.of(question("one"), question("two"), question("three")));
    }

    private static InterviewSession session(ProjectAnalysis analysis) {
        return new InterviewSession("Sample", List.of(result(analysis.questions().get(0), 1),
                result(analysis.questions().get(1), 2), result(analysis.questions().get(2), 3)),
                80, Readiness.STRONG_UNDERSTANDING, 0);
    }

    private static QuestionResult result(TechnicalQuestion question, int number) {
        AnswerEvaluation evaluation = new AnswerEvaluation(Verdict.CORRECT, 80, "Good.",
                List.of("point"), List.of(), Optional.empty());
        return new QuestionResult(number, question,
                new InterviewTurn(TurnType.PRIMARY, question.prompt(), "answer", evaluation), Optional.empty(), 80);
    }

    private static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(id, "How does " + id + " work?", "Understand " + id,
                List.of("point", "detail"), List.of(new CodeEvidence("ArticleScheduler.java", 1, 1, "evidence")));
    }
}
