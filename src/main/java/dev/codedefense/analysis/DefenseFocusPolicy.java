package dev.codedefense.analysis;

import dev.codedefense.domain.DefenseFocus;
import java.util.List;
import java.util.Objects;

public record DefenseFocusPolicy(DefenseFocus focus, String analysisInstruction, List<String> requiredAngles) {
    public DefenseFocusPolicy {
        Objects.requireNonNull(focus); Objects.requireNonNull(analysisInstruction);
        requiredAngles = List.copyOf(requiredAngles);
        if (analysisInstruction.isBlank() || analysisInstruction.length() > 1000 || requiredAngles.size() != 3)
            throw new IllegalArgumentException("focus policy is invalid");
    }
    public static DefenseFocusPolicy forFocus(DefenseFocus focus) {
        String emphasis = switch (focus) {
            case BALANCED -> "intent, trade-offs, failure behavior, and validation";
            case ARCHITECTURE -> "component boundaries, dependencies, state transitions, and compatibility";
            case FAILURE_MODES -> "degradation, retries, partial failure, rollback, and operational assumptions";
            case TESTING -> "observable behavior, edge cases, regression boundaries, and falsifiable tests";
        };
        return new DefenseFocusPolicy(focus,
                "Defense focus: " + focus.displayName() + ". Emphasize " + emphasis
                        + ". Still produce decision, counterfactual, and test-prediction questions.",
                List.of("decision", "counterfactual", "test-prediction"));
    }
    @Override public String toString() { return "DefenseFocusPolicy[focus=" + focus + "]"; }
}
