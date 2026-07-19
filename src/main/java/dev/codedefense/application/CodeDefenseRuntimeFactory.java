package dev.codedefense.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.*; import dev.codedefense.analysis.*; import dev.codedefense.interview.*; import dev.codedefense.terminal.*;
import dev.codedefense.report.*;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.time.Clock;
public final class CodeDefenseRuntimeFactory implements CodeDefenseRuntimeProvider {
 @Override public CodeDefenseRuntime create(PrintWriter output){
  return create(new JLineUserInput(),new ConsoleInterviewOutput(output));
 }
 public CodeDefenseRuntime create(UserInput input,InterviewOutput output){
  CodexRuntimeConfig config=CodexRuntimeConfig.defaults();JdkProcessExecutor executor=new JdkProcessExecutor();CodexProcessEnvironment environment=new CodexProcessEnvironment();ObjectMapper mapper=new ObjectMapper();
  CodexProcessRunner runner=new CodexProcessRunner(executor,new CodexCommandFactory(),environment,config,mapper,CodexTemporaryWorkspace::create,System.getenv());
  CodexPreflight preflight=()->CodexEnvironmentChecker.forCurrentEnvironment(executor,config,environment,Path.of(".").toAbsolutePath().normalize()).checkReady();
  AiProvider provider=new CodexCliAiProvider(preflight,runner);return create(provider,mapper,config,InterviewConfig.defaults(),input,output);
 }
 CodeDefenseRuntime create(AiProvider provider,ObjectMapper mapper,CodexRuntimeConfig config,UserInput input,InterviewOutput output){
  return create(provider, mapper, config, InterviewConfig.defaults(), input, output);
 }
 CodeDefenseRuntime create(AiProvider provider,ObjectMapper mapper,CodexRuntimeConfig config,InterviewConfig interviewConfig,UserInput input,InterviewOutput output){
  ProjectAnalyzer analyzer=new AiProjectAnalyzer(provider,new ProjectAnalysisPromptFactory(),new ProjectAnalysisSchemaLoader(),new ProjectAnalysisValidator(),mapper,config);
  java.util.function.Supplier<StagedChangeAnalyzer> stagedChangeAnalyzerFactory=()->new AiStagedChangeAnalyzer(provider,new StagedChangePromptFactory(),new StagedChangeSchemaLoader(),new StagedChangeAnalysisValidator(),mapper,config);
  AnswerEvaluator evaluator=new AiAnswerEvaluator(provider,new AnswerEvaluationPromptFactory(),new AnswerEvaluationSchemaLoader(),new AnswerEvaluationValidator(),mapper,config,interviewConfig.evaluationTimeout());
  InterviewRunner interview=new InterviewEngine(evaluator,input,output,new InterviewScorer(),new ReadinessClassifier(),interviewConfig);
  Clock clock=Clock.systemUTC(); ReportConfig reportConfig=ReportConfig.defaults();
  ReportNarrativeGenerator narrativeGenerator=new AiReportNarrativeGenerator(provider,new ReportNarrativePromptFactory(),new ReportNarrativeSchemaLoader(),new ReportNarrativeValidator(),mapper,config,reportConfig);
  ReportStore reportStore=new FileSystemReportStore(CodeDefensePaths.defaults(),reportConfig,new MarkdownReportRenderer(),clock);
  ReportService reportService=new UnderstandingReportService(narrativeGenerator,new DeterministicReportNarrativeFactory(),reportStore,clock,config.defaultModel());
  return new CodeDefenseRuntime(analyzer,stagedChangeAnalyzerFactory,interview,reportService);
 }
}
