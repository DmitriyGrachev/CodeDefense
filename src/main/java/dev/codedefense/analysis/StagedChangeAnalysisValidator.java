package dev.codedefense.analysis;

import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectSnapshot;
import java.util.List;

public final class StagedChangeAnalysisValidator {
    private static final String INVALID_RESPONSE_MESSAGE = "Codex returned an invalid staged change analysis.";
    private static final List<String> REQUIRED_IDS = List.of("decision", "counterfactual", "test-prediction");
    private final ProjectAnalysisValidator projectValidator = new ProjectAnalysisValidator();

    public ProjectAnalysis validate(ProjectAnalysis analysis, ProjectSnapshot snapshot) {
        try {
            ProjectAnalysis validated = projectValidator.validate(analysis, snapshot);
            if (!validated.questions().stream().map(question -> question.id()).toList().equals(REQUIRED_IDS)) {
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
