package dev.codedefense.application;

import dev.codedefense.domain.StagedPassportGateResult;
import java.nio.file.Path;

@FunctionalInterface
public interface StagedPassportGateEvaluator {
    StagedPassportGateResult evaluate(Path repository);
}
