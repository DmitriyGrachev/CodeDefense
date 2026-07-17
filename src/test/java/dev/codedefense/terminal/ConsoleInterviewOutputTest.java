package dev.codedefense.terminal;
import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.domain.*;
import java.io.*; import java.util.*;
import org.junit.jupiter.api.Test;
class ConsoleInterviewOutputTest {
 @Test void rendersTheCompleteFinalSummaryFromStoredQuestionScoresWithoutPrivateFields() {
  StringWriter sink=new StringWriter(); ConsoleInterviewOutput out=new ConsoleInterviewOutput(new PrintWriter(sink));
  InterviewSession session=new InterviewSession("demo",List.of(
          result(1,74,"ONE"),result(2,90,"TWO"),result(3,0,"THREE")),
          55,Readiness.REVIEW_NEEDED,0);

  out.renderSummary(session);

  String summary=sink.toString();
  for(String expected:List.of(
          "Technical defense completed.","Question scores","1. 74/100","2. 90/100","3. 0/100",
          "Overall score: 55/100","Readiness: Review needed",
          "Educational signal only. This is not approval to merge or deploy.",
          "Report generation will be connected in Iteration 7.")) assertTrue(summary.contains(expected),expected);
  for(String privateMarker:List.of("ANSWER_ONE","ANSWER_TWO","ANSWER_THREE","PROMPT_ONE","PROMPT_TWO",
          "PROMPT_THREE","KEY_ONE","KEY_TWO","KEY_THREE","FEEDBACK_ONE","FEEDBACK_TWO","FEEDBACK_THREE",
          "REASON_ONE","REASON_TWO","REASON_THREE")) assertFalse(summary.contains(privateMarker),privateMarker);
 }

 @Test void rendersAllRequiredIntroductionInstructions() {
  StringWriter sink=new StringWriter(); ConsoleInterviewOutput out=new ConsoleInterviewOutput(new PrintWriter(sink));

  out.renderIntroduction(3);

  String introduction=sink.toString();
  for(String expected:List.of("Technical defense","Answer each question in one line.",
          "Type 'skip' to skip a question.","Press Ctrl+C to cancel the session.")) assertTrue(introduction.contains(expected),expected);
 }

 @Test void distinguishesSkippedPrimaryAndFollowUpMessages() {
  StringWriter sink=new StringWriter(); ConsoleInterviewOutput out=new ConsoleInterviewOutput(new PrintWriter(sink));

  out.renderSkipped(false);
  out.renderSkipped(true);

  assertTrue(sink.toString().contains("Skipped. Score: 0/100"));
  assertTrue(sink.toString().contains("Follow-up skipped. The primary score is retained."));
 }

 @Test void rendersTheModelEvaluationScoreWithoutRenderingTheFinalQuestionScore() {
  StringWriter sink=new StringWriter(); ConsoleInterviewOutput out=new ConsoleInterviewOutput(new PrintWriter(sink));
  AnswerEvaluation evaluation=new AnswerEvaluation(Verdict.PARTIAL,61,"Useful feedback",List.of(),List.of(),Optional.empty());

  out.renderEvaluation(evaluation);

  assertTrue(sink.toString().contains("Verdict: Partial"));
  assertTrue(sink.toString().contains("Score: 61/100"));
  assertFalse(sink.toString().contains("Question 1 score:"));
 }

 @Test void rendersOnlySafeInterviewFields() {
  StringWriter sink=new StringWriter(); ConsoleInterviewOutput out=new ConsoleInterviewOutput(new PrintWriter(sink));
  TechnicalQuestion q=new TechnicalQuestion("q1","Explain flow\u001b[31m?","GOAL_SECRET",List.of("KEY_SECRET","OTHER_SECRET"),List.of(new CodeEvidence("src/App.java",2,4,"REASON_SECRET")));
  AnswerEvaluation e=new AnswerEvaluation(Verdict.PARTIAL,60,"Useful feedback",List.of("entrypoint"),List.of("failure path"),Optional.of("What fails?"));
  InterviewTurn turn=new InterviewTurn(TurnType.PRIMARY,q.prompt(),"ANSWER_SECRET",e);
  QuestionResult r=new QuestionResult(1,q,turn,Optional.empty(),60);
  InterviewSession session=new InterviewSession("demo",List.of(r,new QuestionResult(2,q,turn,Optional.empty(),60),new QuestionResult(3,q,turn,Optional.empty(),60)),60,Readiness.REVIEW_NEEDED,0);
  out.renderIntroduction(3); out.renderPrimaryQuestion(1,3,q); out.renderEvaluating(); out.renderEvaluation(e); out.renderFollowUp("What fails?"); out.renderSkipped(false); out.renderQuestionScore(1,60); out.renderSummary(session);
  String text=sink.toString();
  for(String expected:List.of("3","src/App.java:2-4","Explain flow?","Useful feedback","entrypoint","failure path","60","Review needed","Report generation will be connected in Iteration 7.")) assertTrue(text.contains(expected),expected);
  for(String secret:List.of("GOAL_SECRET","KEY_SECRET","OTHER_SECRET","REASON_SECRET","ANSWER_SECRET","\u001b")) assertFalse(text.contains(secret),secret);
 }

 private static QuestionResult result(int questionNumber,int score,String marker) {
  TechnicalQuestion question=new TechnicalQuestion("q"+questionNumber,"PROMPT_"+marker,"Goal",
          List.of("KEY_"+marker,"Second key"),List.of(new CodeEvidence("src/App.java",1,1,"REASON_"+marker)));
  AnswerEvaluation evaluation=new AnswerEvaluation(Verdict.PARTIAL,60,"FEEDBACK_"+marker,List.of(),List.of(),Optional.empty());
  InterviewTurn turn=new InterviewTurn(TurnType.PRIMARY,question.prompt(),"ANSWER_"+marker,evaluation);
  return new QuestionResult(questionNumber,question,turn,Optional.empty(),score);
 }
}
