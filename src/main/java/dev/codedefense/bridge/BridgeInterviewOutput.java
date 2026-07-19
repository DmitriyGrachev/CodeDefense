package dev.codedefense.bridge;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.AnswerEvaluation;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.TechnicalQuestion;
import dev.codedefense.terminal.InterviewOutput;
import dev.codedefense.terminal.TerminalTextSanitizer;
import java.util.List;
import java.util.Objects;

/** Emits structured presentation events while leaving scoring to InterviewEngine. */
public final class BridgeInterviewOutput implements InterviewOutput {
    private final BridgeSession session;
    private int currentQuestion;
    private int questionTotal;

    public BridgeInterviewOutput(BridgeSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public void renderIntroduction(int questionCount) {
        questionTotal = questionCount;
    }

    @Override
    public void renderPrimaryQuestion(int current, int total, TechnicalQuestion question) {
        currentQuestion = current;
        questionTotal = total;
        session.emit(new BridgeEvent.QuestionEvent(BridgeProtocol.VERSION, current, total, false,
                sanitize(question.prompt(), "Question unavailable.")));
    }

    @Override
    public void renderInputValidationError(String message) {
        session.emit(new BridgeEvent.ErrorEvent(BridgeProtocol.VERSION, "INVALID_ANSWER",
                sanitize(message, "Answer is invalid."), ExitCodes.INVALID_USAGE));
    }

    @Override
    public void renderEvaluating() {
        // Evaluation progress is represented by the pending question state in the adapter.
    }

    @Override
    public void renderEvaluation(AnswerEvaluation evaluation) {
        session.emit(evaluationEvent(evaluation));
    }

    @Override
    public void renderFollowUp(String followUpQuestion) {
        session.emit(new BridgeEvent.QuestionEvent(BridgeProtocol.VERSION, currentQuestion, questionTotal, true,
                sanitize(followUpQuestion, "Follow-up unavailable.")));
    }

    @Override
    public void renderSkipped(boolean followUp) {
        session.emit(evaluationEvent(AnswerEvaluation.skipped()));
    }

    @Override
    public void renderQuestionScore(int questionNumber, int score) {
        session.emit(new BridgeEvent.QuestionScoreEvent(BridgeProtocol.VERSION, questionNumber, score));
    }

    @Override
    public void renderSummary(InterviewSession interview) {
        session.emit(new BridgeEvent.SummaryEvent(BridgeProtocol.VERSION,
                interview.results().stream().map(result -> result.finalScore()).toList(),
                interview.overallScore(), interview.readiness().displayName()));
    }

    private BridgeEvent.EvaluationEvent evaluationEvent(AnswerEvaluation evaluation) {
        return new BridgeEvent.EvaluationEvent(BridgeProtocol.VERSION, evaluation.verdict().name(), evaluation.score(),
                sanitize(evaluation.feedback(), "Evaluation unavailable."),
                sanitize(evaluation.understoodConcepts()), sanitize(evaluation.missingConcepts()));
    }

    private static List<String> sanitize(List<String> values) {
        return values.stream().map(TerminalTextSanitizer::singleLine)
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    private static String sanitize(String value, String fallback) {
        String sanitized = TerminalTextSanitizer.singleLine(value);
        return sanitized.isBlank() ? fallback : sanitized;
    }
}
