package dev.codedefense.application;

import dev.codedefense.sample.SampleProjectExtractor;
import java.io.PrintWriter;
import java.util.Objects;

/** Prepares the embedded project for one delegated technical-defense workflow run. */
public final class RunSampleUseCase implements SampleProjectRunner {
    private final SampleProjectExtractor extractor;
    private final ProjectDefenseRunner projectDefenseRunner;

    public RunSampleUseCase(SampleProjectExtractor extractor, ProjectDefenseRunner projectDefenseRunner) {
        this.extractor = Objects.requireNonNull(extractor, "Sample project extractor");
        this.projectDefenseRunner = Objects.requireNonNull(projectDefenseRunner, "Project defense runner");
    }

    @Override
    public int run(boolean dryRun, boolean skipConfirmation, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(out, "Command output");
        Objects.requireNonNull(err, "Command error output");
        out.println("Mode: Embedded sample");
        out.println("Preparing built-in sample project...");
        try (SampleProjectExtractor.ExtractedSampleProject extracted = extractor.extract()) {
            return projectDefenseRunner.run(extracted.projectRoot(), dryRun, skipConfirmation, out, err);
        }
    }
}
