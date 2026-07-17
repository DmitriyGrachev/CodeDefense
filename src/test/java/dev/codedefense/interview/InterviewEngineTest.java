package dev.codedefense.interview;
import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.domain.*; import dev.codedefense.terminal.*;
import java.util.*; import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
class InterviewEngineTest {
 @Test void conductsThreeQuestionsAndAtMostOneFollowUpEach() {
  AtomicInteger calls=new AtomicInteger();
  AnswerEvaluator evaluator=req->{calls.incrementAndGet(); return req.stage()==EvaluationStage.PRIMARY
    ? new AnswerEvaluation(Verdict.PARTIAL,50,"Primary feedback",List.of(),List.of("risk"),Optional.of("Explain the failure path."))
    : new AnswerEvaluation(Verdict.PARTIAL,90,"Follow-up feedback",List.of("risk"),List.of(),Optional.of("MUST_NOT_BE_ASKED"));};
  RecordingOutput output=new RecordingOutput();
  InterviewSession session=engine(evaluator,new QueueInput("a1","f1","a2","f2","a3","f3"),output).conduct(analysis());
  assertEquals(6,calls.get()); assertEquals(3,session.results().size()); assertEquals(74,session.overallScore());
  assertEquals(List.of(1,2,3),output.questionNumbers); assertFalse(output.followUps.contains("MUST_NOT_BE_ASKED"));
 }
 @Test void exactSkipIsLocalButSkipPhrasesAreEvaluated() {
  AtomicInteger calls=new AtomicInteger(); AnswerEvaluator evaluator=req->{calls.incrementAndGet();return correct();};
  InterviewSession session=engine(evaluator,new QueueInput(" SKIP ","please skip this","answer"),new RecordingOutput()).conduct(analysis());
  assertEquals(2,calls.get()); assertEquals(1,session.skippedQuestionCount()); assertEquals(60,session.overallScore());
 }
 @Test void neverAsksAnArtificialFollowUpForACorrectEvaluation() {
  AtomicInteger calls=new AtomicInteger(); RecordingOutput output=new RecordingOutput();
  AnswerEvaluator evaluator=request->{calls.incrementAndGet();return new AnswerEvaluation(Verdict.CORRECT,90,"Correct feedback",List.of(),List.of(),Optional.of("This must not be asked."));};

  InterviewSession session=engine(evaluator,new QueueInput("first","second","third"),output).conduct(analysis());

  assertEquals(3,calls.get()); assertTrue(output.followUps.isEmpty()); assertEquals(90,session.overallScore());
 }
 @Test void retriesBlankAndOverlongAnswersWithoutEvaluation() {
  AtomicInteger calls=new AtomicInteger(); AnswerEvaluator evaluator=req->{calls.incrementAndGet();return correct();}; RecordingOutput output=new RecordingOutput();
  engine(evaluator,new QueueInput(" ","x".repeat(8001),"ok","ok","ok"),output).conduct(analysis());
  assertEquals(3,calls.get()); assertEquals(2,output.validationErrors);
 }
 @Test void skippedFollowUpRetainsPrimaryScoreWithoutSecondCall() {
  AtomicInteger calls=new AtomicInteger(); AnswerEvaluator evaluator=req->{calls.incrementAndGet();return partial();};
  InterviewSession session=engine(evaluator,new QueueInput("a","skip","skip","skip"),new RecordingOutput()).conduct(analysis());
  assertEquals(1,calls.get()); assertEquals(50,session.results().getFirst().finalScore()); assertEquals(2,session.skippedQuestionCount());
 }
 @Test void propagatesEvaluationFailureAndCancellation() {
  RuntimeException failure=new RuntimeException("safe failure");
  assertSame(failure,assertThrows(RuntimeException.class,()->engine(req->{throw failure;},new QueueInput("a"),new RecordingOutput()).conduct(analysis())));
  assertThrows(InterviewCancelledException.class,()->engine(req->correct(),prompt->{throw new InterviewCancelledException("cancel");},new RecordingOutput()).conduct(analysis()));
 }
 private static InterviewEngine engine(AnswerEvaluator e,UserInput i,InterviewOutput o){return new InterviewEngine(e,i,o,new InterviewScorer(),new ReadinessClassifier(),InterviewConfig.defaults());}
 private static AnswerEvaluation correct(){return new AnswerEvaluation(Verdict.CORRECT,90,"Correct answer feedback",List.of(),List.of(),Optional.empty());}
 private static AnswerEvaluation partial(){return new AnswerEvaluation(Verdict.PARTIAL,50,"Partial answer feedback",List.of(),List.of(),Optional.of("Follow up?"));}
 private static ProjectAnalysis analysis(){List<TechnicalQuestion> qs=new ArrayList<>();for(int n=1;n<=3;n++)qs.add(new TechnicalQuestion("q"+n,"Question prompt number "+n,"Goal",List.of("one","two"),List.of(new CodeEvidence("src/App.java",1,2,"reason"))));return new ProjectAnalysis("demo","Java CLI","A sufficiently descriptive project summary.",List.of("first flow","second flow"),List.of(new ProjectComponent("App","class","Runs the application",List.of("src/App.java"))),List.of("startup","errors"),qs);}
 private static final class QueueInput implements UserInput {private final ArrayDeque<String> values;QueueInput(String...v){values=new ArrayDeque<>(List.of(v));}public String readAnswer(String p){return values.removeFirst();}}
 private static final class RecordingOutput implements InterviewOutput {final List<Integer> questionNumbers=new ArrayList<>();final List<String> followUps=new ArrayList<>();int validationErrors;public void renderIntroduction(int c){}public void renderPrimaryQuestion(int c,int t,TechnicalQuestion q){questionNumbers.add(c);}public void renderInputValidationError(String m){validationErrors++;}public void renderEvaluating(){}public void renderEvaluation(AnswerEvaluation e){}public void renderFollowUp(String q){followUps.add(q);}public void renderSkipped(boolean f){}public void renderQuestionScore(int n,int s){}public void renderSummary(InterviewSession s){}}
}
