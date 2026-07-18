package dev.codedefense.application;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.*; import dev.codedefense.interview.*; import dev.codedefense.terminal.*;
import java.lang.reflect.Field; import java.nio.file.Files; import java.nio.file.Path; import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class CodeDefenseRuntimeFactoryTest {
 @TempDir Path userHome;
 @Test void productionConstructionDefersFilesystemProcessPreflightModelJlineAndReportWork() throws Exception {
  AtomicInteger inputReads=new AtomicInteger();
  String originalHome=System.getProperty("user.home"); System.setProperty("user.home",userHome.toString());
  try {
   CodeDefenseRuntime runtime=new CodeDefenseRuntimeFactory().create(prompt->{inputReads.incrementAndGet();throw new AssertionError();},new NoOutput());

   Object provider=field(runtime.analyzer(),"aiProvider"); Object preflight=field(provider,"preflight");
   assertFalse(preflight instanceof CodexEnvironmentChecker);
   assertEquals(0,inputReads.get()); assertFalse(Files.exists(userHome.resolve(".codedefense")));
  } finally { if(originalHome==null)System.clearProperty("user.home");else System.setProperty("user.home",originalHome); }
 }
 @Test void sharesOneProviderAndFactoryConstructionHasNoCallsOrInputReads() throws Exception {
  AtomicInteger providerCalls=new AtomicInteger(),inputReads=new AtomicInteger(); AiProvider provider=r->{providerCalls.incrementAndGet();throw new AssertionError();};
  CodeDefenseRuntime runtime=new CodeDefenseRuntimeFactory().create(provider,new ObjectMapper(),CodexRuntimeConfig.defaults(),p->{inputReads.incrementAndGet();return "skip";},new NoOutput());
  Object analyzerProvider=field(runtime.analyzer(),"aiProvider"); Object evaluator=field(runtime.interviewRunner(),"evaluator"); Object evaluatorProvider=field(evaluator,"provider");
  Object generator=field(runtime.reportService(), "narrativeGenerator"); Object narrativeProvider=field(generator,"provider");
  assertSame(provider,analyzerProvider);assertSame(provider,evaluatorProvider);assertSame(provider,narrativeProvider);assertEquals(0,providerCalls.get());assertEquals(0,inputReads.get());
 }
 @Test void defersStagedAnalyzerConstructionUntilTheLaterWorkflowRequestsIt() throws Exception {
  AtomicInteger providerCalls=new AtomicInteger(); AiProvider provider=r->{providerCalls.incrementAndGet();throw new AssertionError();};
  CodeDefenseRuntime runtime=new CodeDefenseRuntimeFactory().create(provider,new ObjectMapper(),CodexRuntimeConfig.defaults(),p->"skip",new NoOutput());
  assertFalse((Object) runtime.stagedChangeAnalyzerFactory() instanceof dev.codedefense.analysis.AiStagedChangeAnalyzer);
  assertEquals(0,providerCalls.get());
  Object stagedProvider=field(runtime.stagedChangeAnalyzer(),"aiProvider");
  assertSame(provider,stagedProvider);assertEquals(0,providerCalls.get());
 }
 private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private static final class NoOutput implements InterviewOutput {public void renderIntroduction(int c){}public void renderPrimaryQuestion(int c,int t,dev.codedefense.domain.TechnicalQuestion q){}public void renderInputValidationError(String m){}public void renderEvaluating(){}public void renderEvaluation(dev.codedefense.domain.AnswerEvaluation e){}public void renderFollowUp(String q){}public void renderSkipped(boolean f){}public void renderQuestionScore(int n,int s){}public void renderSummary(dev.codedefense.domain.InterviewSession s){}}
}
