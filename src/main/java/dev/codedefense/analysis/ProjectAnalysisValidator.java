package dev.codedefense.analysis;

import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.CodeEvidence;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.ProjectComponent;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.TechnicalQuestion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProjectAnalysisValidator {
    private static final String INVALID_RESPONSE_MESSAGE = "Codex returned an invalid project analysis.";
    private static final Pattern QUESTION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");

    public ProjectAnalysis validate(ProjectAnalysis analysis, ProjectSnapshot snapshot) {
        try {
            return validateAnalysis(analysis, snapshot);
        } catch (InvalidCodexResponseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private static ProjectAnalysis validateAnalysis(ProjectAnalysis analysis, ProjectSnapshot snapshot) {
        if (analysis == null || snapshot == null
                || !boundedText(analysis.projectName(), 1, 120)
                || !boundedText(analysis.projectType(), 1, 120)
                || !boundedText(analysis.summary(), 20, 1200)
                || !boundedTextList(analysis.mainFlow(), 2, 8, 3, 300)
                || !boundedTextList(analysis.criticalTopics(), 2, 8, 2, 160)
                || analysis.questions().size() != 3) {
            throw invalidResponse();
        }

        Map<String, Integer> selectedLines = selectedLines(snapshot);
        List<ProjectComponent> components = validateComponents(analysis.components(), selectedLines);
        List<TechnicalQuestion> questions = validateQuestions(analysis.questions(), selectedLines);
        validateUniqueNormalized(analysis.criticalTopics());

        return new ProjectAnalysis(
                snapshot.projectName(), snapshot.projectType(), analysis.summary(), analysis.mainFlow(),
                components, analysis.criticalTopics(), questions);
    }

    private static Map<String, Integer> selectedLines(ProjectSnapshot snapshot) {
        Map<String, Integer> selected = new LinkedHashMap<>();
        for (ProjectSnapshot.SelectedFile file : snapshot.selectedFiles()) {
            if (file == null || file.includedLines() < 1) {
                throw invalidResponse();
            }
            String path = normalizeSelectedPath(file.relativePath());
            if (selected.put(path, file.includedLines()) != null) {
                throw invalidResponse();
            }
        }
        return Map.copyOf(selected);
    }

    private static List<ProjectComponent> validateComponents(
            List<ProjectComponent> components, Map<String, Integer> selectedLines) {
        if (!boundedList(components, 1, 12)) {
            throw invalidResponse();
        }
        List<ProjectComponent> normalized = new ArrayList<>(components.size());
        Set<ComponentIdentity> identities = new HashSet<>();
        for (ProjectComponent component : components) {
            if (component == null
                    || !boundedText(component.name(), 1, 120)
                    || !boundedText(component.kind(), 1, 64)
                    || !boundedText(component.responsibility(), 5, 500)
                    || !boundedList(component.paths(), 1, 5)) {
                throw invalidResponse();
            }
            List<String> paths = normalizePaths(component.paths(), selectedLines);
            List<String> sortedPaths = paths.stream().sorted().toList();
            if (!identities.add(new ComponentIdentity(normalizeText(component.name()), sortedPaths))) {
                throw invalidResponse();
            }
            normalized.add(new ProjectComponent(component.name(), component.kind(), component.responsibility(), paths));
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizePaths(List<String> paths, Map<String, Integer> selectedLines) {
        List<String> normalized = new ArrayList<>(paths.size());
        Set<String> unique = new HashSet<>();
        for (String path : paths) {
            if (!boundedText(path, 1, 300)) {
                throw invalidResponse();
            }
            String portable = normalizeModelPath(path);
            if (!selectedLines.containsKey(portable) || !unique.add(portable)) {
                throw invalidResponse();
            }
            normalized.add(portable);
        }
        return List.copyOf(normalized);
    }

    private static List<TechnicalQuestion> validateQuestions(
            List<TechnicalQuestion> questions, Map<String, Integer> selectedLines) {
        Set<String> ids = new HashSet<>();
        Set<String> prompts = new HashSet<>();
        List<TechnicalQuestion> normalized = new ArrayList<>(questions.size());
        for (TechnicalQuestion question : questions) {
            if (question == null
                    || !boundedText(question.id(), 1, 64)
                    || !QUESTION_ID.matcher(question.id()).matches()
                    || !boundedText(question.prompt(), 10, 500)
                    || !boundedText(question.learningGoal(), 3, 240)
                    || !boundedTextList(question.expectedKeyPoints(), 2, 6, 2, 300)
                    || !boundedList(question.evidence(), 1, 3)
                    || !ids.add(question.id())
                    || !prompts.add(normalizeText(question.prompt()))) {
                throw invalidResponse();
            }
            validateUniqueNormalized(question.expectedKeyPoints());
            List<CodeEvidence> evidence = validateEvidence(question.evidence(), selectedLines);
            normalized.add(new TechnicalQuestion(
                    question.id(), question.prompt(), question.learningGoal(), question.expectedKeyPoints(), evidence));
        }
        return List.copyOf(normalized);
    }

    private static List<CodeEvidence> validateEvidence(
            List<CodeEvidence> evidence, Map<String, Integer> selectedLines) {
        Set<EvidenceLocation> locations = new HashSet<>();
        List<CodeEvidence> normalized = new ArrayList<>(evidence.size());
        for (CodeEvidence item : evidence) {
            if (item == null || !boundedText(item.path(), 1, 300) || !boundedText(item.reason(), 3, 300)
                    || item.startLine() < 1 || item.endLine() < item.startLine()) {
                throw invalidResponse();
            }
            String path = normalizeModelPath(item.path());
            Integer includedLines = selectedLines.get(path);
            EvidenceLocation location = new EvidenceLocation(path, item.startLine(), item.endLine());
            if (includedLines == null || item.endLine() > includedLines || !locations.add(location)) {
                throw invalidResponse();
            }
            normalized.add(new CodeEvidence(path, item.startLine(), item.endLine(), item.reason()));
        }
        return List.copyOf(normalized);
    }

    private static void validateUniqueNormalized(List<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (!normalized.add(normalizeText(value))) {
                throw invalidResponse();
            }
        }
    }

    private static String normalizeSelectedPath(Path path) {
        if (path == null) {
            throw invalidResponse();
        }
        return normalizeModelPath(path.toString());
    }

    private static String normalizeModelPath(String path) {
        String portable = path.replace('\\', '/');
        if (portable.isBlank() || portable.startsWith("/")
                || (portable.length() >= 2 && Character.isLetter(portable.charAt(0)) && portable.charAt(1) == ':')) {
            throw invalidResponse();
        }
        String[] segments = portable.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw invalidResponse();
            }
        }
        return String.join("/", segments);
    }

    private static String normalizeText(String value) {
        return REPEATED_WHITESPACE.matcher(value.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static boolean boundedText(String value, int minimumLength, int maximumLength) {
        return value != null && !value.isBlank()
                && value.strip().length() >= minimumLength && value.strip().length() <= maximumLength;
    }

    private static boolean boundedTextList(
            List<String> values, int minimumItems, int maximumItems, int minimumLength, int maximumLength) {
        if (!boundedList(values, minimumItems, maximumItems)) {
            return false;
        }
        return values.stream().allMatch(value -> boundedText(value, minimumLength, maximumLength));
    }

    private static boolean boundedList(List<?> values, int minimumItems, int maximumItems) {
        return values != null && values.size() >= minimumItems && values.size() <= maximumItems;
    }

    private static InvalidCodexResponseException invalidResponse() {
        return new InvalidCodexResponseException(INVALID_RESPONSE_MESSAGE);
    }

    private record EvidenceLocation(String path, int startLine, int endLine) {
    }

    private record ComponentIdentity(String normalizedName, List<String> sortedPaths) {
    }
}
