package dev.codedefense.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.*; import dev.codedefense.analysis.*; import dev.codedefense.interview.*; import dev.codedefense.terminal.*;
import java.nio.file.Path;
public final class CodeDefenseRuntimeFactory {
 public CodeDefenseRuntime create(UserInput input,InterviewOutput output){
  CodexRuntimeConfig config=CodexRuntimeConfig.defaults();JdkProcessExecutor executor=new JdkProcessExecutor();CodexProcessEnvironment environment=new CodexProcessEnvironment();ObjectMapper mapper=new ObjectMapper();
  CodexEnvironmentChecker preflight=CodexEnvironmentChecker.forCurrentEnvironment(executor,config,environment,Path.of(".").toAbsolutePath().normalize());
  CodexProcessRunner runner=new CodexProcessRunner(executor,new CodexCommandFactory(),environment,config,mapper,CodexTemporaryWorkspace::create,System.getenv());
  AiProvider provider=new CodexCliAiProvider(preflight,runner);return create(provider,mapper,config,input,output);
 }
 CodeDefenseRuntime create(AiProvider provider,ObjectMapper mapper,CodexRuntimeConfig config,UserInput input,InterviewOutput output){
  ProjectAnalyzer analyzer=new AiProjectAnalyzer(provider,new ProjectAnalysisPromptFactory(),new ProjectAnalysisSchemaLoader(),new ProjectAnalysisValidator(),mapper,config);
  AnswerEvaluator evaluator=new AiAnswerEvaluator(provider,new AnswerEvaluationPromptFactory(),new AnswerEvaluationSchemaLoader(),new AnswerEvaluationValidator(),mapper,config);
  InterviewRunner interview=new InterviewEngine(evaluator,input,output,new InterviewScorer(),new ReadinessClassifier(),InterviewConfig.defaults());return new CodeDefenseRuntime(analyzer,interview);
 }
}
