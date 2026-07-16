package dev.codedefense.domain;

import java.util.List;
import java.util.Objects;

public record ProjectAnalysis(
        String projectName,
        String projectType,
        String summary,
        List<String> mainFlow,
        List<ProjectComponent> components,
        List<String> criticalTopics,
        List<TechnicalQuestion> questions) {
    public ProjectAnalysis {
        projectName = CodeEvidence.requireNonBlank(projectName, "projectName");
        projectType = CodeEvidence.requireNonBlank(projectType, "projectType");
        summary = CodeEvidence.requireNonBlank(summary, "summary");
        mainFlow = CodeEvidence.copyNonBlankStrings(mainFlow, "mainFlow");
        requireSize(mainFlow, 2, 8, "mainFlow");
        Objects.requireNonNull(components, "components");
        components = List.copyOf(components);
        requireSize(components, 1, 12, "components");
        if (components.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("components cannot contain null");
        }
        criticalTopics = CodeEvidence.copyNonBlankStrings(criticalTopics, "criticalTopics");
        requireSize(criticalTopics, 2, 8, "criticalTopics");
        Objects.requireNonNull(questions, "questions");
        questions = List.copyOf(questions);
        if (questions.size() != 3 || questions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Project analysis requires exactly three questions");
        }
    }

    private static void requireSize(List<?> values, int minimum, int maximum, String field) {
        if (values.size() < minimum || values.size() > maximum) {
            throw new IllegalArgumentException(field + " has an invalid size");
        }
    }

    @Override
    public String toString() {
        return "ProjectAnalysis[projectName=" + projectName
                + ", projectType=" + projectType
                + ", summary=" + summary
                + ", mainFlow=" + mainFlow
                + ", components=" + components
                + ", criticalTopics=" + criticalTopics
                + ", questionCount=" + questions.size() + "]";
    }
}
