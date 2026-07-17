package dev.codedefense.terminal;
import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.domain.*;
import java.io.*; import java.util.*;
import org.junit.jupiter.api.Test;
class ConsoleInterviewOutputTest {
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
}
