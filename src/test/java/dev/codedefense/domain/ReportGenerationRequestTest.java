package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReportGenerationRequestTest {
    @Test
    void requiresMatchingProjectAndQuestionIdentityAndOrder() {
        ProjectAnalysis analysis = analysis("project", List.of("q1", "q2", "q3"));
        assertDoesNotThrow(() -> new ReportGenerationRequest(analysis, session("project", List.of("q1", "q2", "q3"))));
        assertThrows(IllegalArgumentException.class, () -> new ReportGenerationRequest(analysis, session("other", List.of("q1", "q2", "q3"))));
        assertThrows(IllegalArgumentException.class, () -> new ReportGenerationRequest(analysis, session("project", List.of("q2", "q1", "q3"))));
    }

    static ProjectAnalysis analysis(String name, List<String> ids) {
        return new ProjectAnalysis(name, "Java", "summary", List.of("one", "two"),
                List.of(new ProjectComponent("core", "component", "responsibility", List.of("src/App.java"))), List.of("topic one", "topic two"),
                ids.stream().map(ReportGenerationRequestTest::question).toList());
    }

    static InterviewSession session(String name, List<String> ids) {
        return new InterviewSession(name, java.util.stream.IntStream.range(0, 3).mapToObj(index ->
                new QuestionResult(index + 1, question(ids.get(index)), primary(), java.util.Optional.empty(), 60)).toList(),
                60, Readiness.REVIEW_NEEDED, 0);
    }

    static TechnicalQuestion question(String id) {
        return new TechnicalQuestion(id, "QUESTION_PROMPT_SECRET", "goal", List.of("key one", "key two"),
                List.of(new CodeEvidence("src/App.java", 1, 2, "reason")));
    }

    static InterviewTurn primary() {
        return new InterviewTurn(TurnType.PRIMARY, "QUESTION_PROMPT_SECRET", "ANSWER_SECRET",
                new AnswerEvaluation(Verdict.PARTIAL, 60, "FEEDBACK_SECRET", List.of(), List.of(), java.util.Optional.empty()));
    }
}
