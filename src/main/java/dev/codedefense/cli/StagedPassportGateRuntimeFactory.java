package dev.codedefense.cli;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.application.EvaluateStagedPassportGateUseCase;
import dev.codedefense.application.StagedPassportGateEvaluator;
import dev.codedefense.change.GitCliStagedChangeSource;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import java.time.Clock;

final class StagedPassportGateRuntimeFactory {
    private StagedPassportGateRuntimeFactory() {
    }

    static StagedPassportGateEvaluator create() {
        return new EvaluateStagedPassportGateUseCase(
                new GitCliStagedChangeSource(new JdkProcessExecutor()),
                new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                        new MarkdownChangePassportRenderer(), Clock.systemUTC()));
    }
}
