package dev.codedefense.application;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.*; import dev.codedefense.interview.*; import dev.codedefense.terminal.*;
import java.lang.reflect.Field; import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
class CodeDefenseRuntimeFactoryTest {
 @Test void sharesOneProviderAndFactoryConstructionHasNoCallsOrInputReads() throws Exception {
  AtomicInteger providerCalls=new AtomicInteger(),inputReads=new AtomicInteger(); AiProvider provider=r->{providerCalls.incrementAndGet();throw new AssertionError();};
  CodeDefenseRuntime runtime=new CodeDefenseRuntimeFactory().create(provider,new ObjectMapper(),CodexRuntimeConfig.defaults(),p->{inputReads.incrementAndGet();return "skip";},new NoOutput());
  Object analyzerProvider=field(runtime.analyzer(),"aiProvider"); Object evaluator=field(runtime.interviewRunner(),"evaluator"); Object evaluatorProvider=field(evaluator,"provider");
  assertSame(provider,analyzerProvider);assertSame(provider,evaluatorProvider);assertEquals(0,providerCalls.get());assertEquals(0,inputReads.get());
 }
 private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static final class NoOutput implements InterviewOutput {public void renderIntroduction(int c){}public void renderPrimaryQuestion(int c,int t,dev.codedefense.domain.TechnicalQuestion q){}public void renderInputValidationError(String m){}public void renderEvaluating(){}public void renderEvaluation(dev.codedefense.domain.AnswerEvaluation e){}public void renderFollowUp(String q){}public void renderSkipped(boolean f){}public void renderQuestionScore(int n,int s){}public void renderSummary(dev.codedefense.domain.InterviewSession s){}}
}
