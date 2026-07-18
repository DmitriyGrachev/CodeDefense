package dev.codedefense.analysis;

import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import java.util.Set;

public final class StagedChangeAnalysisValidator {
    private static final String INVALID_RESPONSE_MESSAGE = "Codex returned an invalid staged change analysis.";
    private static final Set<String> REQUIRED_IDS = Set.of("decision", "counterfactual", "test-prediction");
    private final ProjectAnalysisValidator projectValidator = new ProjectAnalysisValidator();

    public ProjectAnalysis validate(ProjectAnalysis analysis, ProjectSnapshot snapshot) {
        try {
            ProjectAnalysis validated = projectValidator.validate(analysis, snapshot);
            if (!validated.questions().stream().map(question -> question.id()).collect(java.util.stream.Collectors.toSet())
                    .equals(REQUIRED_IDS)) {
                throw invalid();
            }
            return validated;
        } catch (InvalidCodexResponseException exception) {
            throw invalid();
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private static InvalidCodexResponseException invalid() {
        return new InvalidCodexResponseException(INVALID_RESPONSE_MESSAGE);
    }
}
