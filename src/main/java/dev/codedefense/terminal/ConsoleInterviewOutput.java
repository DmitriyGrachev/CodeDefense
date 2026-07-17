package dev.codedefense.terminal;
import dev.codedefense.domain.*; import java.io.PrintWriter; import java.util.Objects;
public final class ConsoleInterviewOutput implements InterviewOutput {
 private final PrintWriter output;
 public ConsoleInterviewOutput(PrintWriter output){this.output=Objects.requireNonNull(output);}
 public void renderIntroduction(int count){output.printf("%nTechnical defense: %d questions. Enter skip to skip a question.%n",count);flush();}
 public void renderPrimaryQuestion(int current,int total,TechnicalQuestion q){output.printf("%nQuestion %d/%d%n",current,total);q.evidence().forEach(e->output.printf("Evidence: %s:%d-%d%n",safe(e.path()),e.startLine(),e.endLine()));output.println(safe(q.prompt()));flush();}
 public void renderInputValidationError(String message){output.println(safe(message));flush();}
 public void renderEvaluating(){output.println("Evaluating answer...");flush();}
 public void renderEvaluation(AnswerEvaluation e){output.printf("Verdict: %s%nFeedback: %s%n",e.verdict(),safe(e.feedback()));if(!e.understoodConcepts().isEmpty())output.println("Understood: "+e.understoodConcepts().stream().map(ConsoleInterviewOutput::safe).reduce((a,b)->a+", "+b).orElse(""));if(!e.missingConcepts().isEmpty())output.println("Missing: "+e.missingConcepts().stream().map(ConsoleInterviewOutput::safe).reduce((a,b)->a+", "+b).orElse(""));flush();}
 public void renderFollowUp(String q){output.println("Follow-up: "+safe(q));flush();}
 public void renderSkipped(boolean followUp){output.println(followUp?"Follow-up skipped.":"Question skipped.");flush();}
 public void renderQuestionScore(int number,int score){output.printf("Question %d score: %d/100%n",number,score);flush();}
 public void renderSummary(InterviewSession s){output.printf("%nFinal score: %d/100%nReadiness: %s%n",s.overallScore(),safe(s.readiness().displayName()));output.println("This assessment is educational and based on the submitted answers.");output.println("Report generation will be connected in Iteration 7.");flush();}
 private void flush(){output.flush();} private static String safe(String value){return TerminalTextSanitizer.singleLine(value);}
}
