package dev.codedefense.terminal;
import dev.codedefense.domain.*;
public interface InterviewOutput {
 void renderIntroduction(int questionCount); void renderPrimaryQuestion(int current,int total,TechnicalQuestion question);
 void renderInputValidationError(String message); void renderEvaluating(); void renderEvaluation(AnswerEvaluation evaluation);
 void renderFollowUp(String followUpQuestion); void renderSkipped(boolean followUp); void renderQuestionScore(int questionNumber,int score);
 void renderSummary(InterviewSession session);
}
