package dev.codedefense.application;

import dev.codedefense.sample.SampleProjectExtractor;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunSampleUseCaseTest {
    @Test
    void preparesTheSampleDelegatesExactlyOnceAndAlwaysClosesTheWorkspace() {
        CapturingRunner runner = new CapturingRunner(41);
        RunSampleUseCase useCase = new RunSampleUseCase(new SampleProjectExtractor(), runner);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintWriter out = writer(output);
        PrintWriter err = writer(error);

        assertEquals(41, useCase.run(true, false, out, err));

        assertEquals(1, runner.calls);
        assertEquals("codedefense-sample-news-service", runner.projectRoot.getFileName().toString());
        assertFalse(Files.exists(runner.projectRoot.getParent()));
        assertEquals("# News Service sample", runner.readmeFirstLine);
        assertTrue(runner.dryRun);
        assertFalse(runner.skipConfirmation);
        assertTrue(runner.out == out);
        assertTrue(runner.err == err);
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Mode: Embedded sample"));
        assertTrue(text.contains("Preparing built-in sample project..."));
    }

    @Test
    void propagatesEveryRunnerExitCodeAndRemovesTheWorkspace() {
        for (int exitCode : new int[] {0, 5, 6, 7, 8, 9, 130}) {
            CapturingRunner runner = new CapturingRunner(exitCode);
            RunSampleUseCase useCase = new RunSampleUseCase(new SampleProjectExtractor(), runner);

            assertEquals(exitCode, useCase.run(false, true,
                    writer(new ByteArrayOutputStream()), writer(new ByteArrayOutputStream())));
            assertEquals(1, runner.calls);
            assertFalse(runner.dryRun);
            assertTrue(runner.skipConfirmation);
            assertFalse(Files.exists(runner.projectRoot.getParent()));
        }
    }

    @Test
    void closesTheWorkspaceWhenTheRunnerFailsUnexpectedly() {
        FailingRunner runner = new FailingRunner();
        RunSampleUseCase useCase = new RunSampleUseCase(new SampleProjectExtractor(), runner);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> useCase.run(true, false, writer(new ByteArrayOutputStream()), writer(new ByteArrayOutputStream())));

        assertEquals("runner fixture failure", exception.getMessage());
        assertFalse(Files.exists(runner.projectRoot.getParent()));
    }

    private static PrintWriter writer(ByteArrayOutputStream output) {
        return new PrintWriter(output, true, StandardCharsets.UTF_8);
    }

    private static final class CapturingRunner implements ProjectDefenseRunner {
        private final int result;
        private int calls;
        private Path projectRoot;
        private boolean dryRun;
        private boolean skipConfirmation;
        private PrintWriter out;
        private PrintWriter err;
        private String readmeFirstLine;

        private CapturingRunner(int result) {
            this.result = result;
        }

        @Override
        public int run(Path projectPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err) {
            calls++;
            projectRoot = projectPath;
            this.dryRun = dryRun;
            this.skipConfirmation = skipConfirmation;
            this.out = out;
            this.err = err;
            assertTrue(Files.isDirectory(projectPath));
            assertTrue(Files.exists(projectPath.resolve("pom.xml")));
            try {
                readmeFirstLine = Files.readAllLines(projectPath.resolve("README.md")).getFirst();
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
            return result;
        }
    }

    private static final class FailingRunner implements ProjectDefenseRunner {
        private Path projectRoot;

        @Override
        public int run(Path projectPath, boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err) {
            projectRoot = projectPath;
            throw new IllegalStateException("runner fixture failure");
        }
    }
}
