package dev.codedefense.terminal;
import dev.codedefense.domain.*; import java.io.PrintWriter; import java.util.Objects;
public final class ConsoleInterviewOutput implements InterviewOutput {
 private final PrintWriter output;
 public ConsoleInterviewOutput(PrintWriter output){this.output=Objects.requireNonNull(output);}
 public void renderIntroduction(int count){output.printf("%nTechnical defense%nQuestions: %d%nAnswer each question in one line.%nType 'skip' to skip a question.%nPress Ctrl+C to cancel the session.%n",count);flush();}
 public void renderPrimaryQuestion(int current,int total,TechnicalQuestion q){output.printf("%nQuestion %d/%d%n",current,total);q.evidence().forEach(e->output.printf("Evidence: %s:%d-%d%n",safe(e.path()),e.startLine(),e.endLine()));output.println(safe(q.prompt()));flush();}
 public void renderInputValidationError(String message){output.println(safe(message));flush();}
 public void renderEvaluating(){output.println("Evaluating answer...");flush();}
 public void renderEvaluation(AnswerEvaluation e){output.printf("Verdict: %s%nScore: %d/100%nFeedback: %s%n",verdictLabel(e.verdict()),e.score(),safe(e.feedback()));if(!e.understoodConcepts().isEmpty())output.println("Understood: "+e.understoodConcepts().stream().map(ConsoleInterviewOutput::safe).reduce((a,b)->a+", "+b).orElse(""));if(!e.missingConcepts().isEmpty())output.println("Missing: "+e.missingConcepts().stream().map(ConsoleInterviewOutput::safe).reduce((a,b)->a+", "+b).orElse(""));flush();}
 public void renderFollowUp(String q){output.println("Follow-up: "+safe(q));flush();}
 public void renderSkipped(boolean followUp){output.println(followUp?"Follow-up skipped. The primary score is retained.":"Skipped. Score: 0/100");flush();}
 public void renderQuestionScore(int number,int score){output.printf("Question %d score: %d/100%n",number,score);flush();}
 public void renderSummary(InterviewSession s){output.printf("%nTechnical defense completed.%n%nQuestion scores%n");for(int index=0;index<s.results().size();index++)output.printf("%d. %d/100%n",index+1,s.results().get(index).finalScore());output.printf("%nOverall score: %d/100%nReadiness: %s%n%n",s.overallScore(),safe(s.readiness().displayName()));output.println("Educational signal only. This is not approval to merge or deploy.");flush();}
 private void flush(){output.flush();} private static String safe(String value){return TerminalTextSanitizer.singleLine(value);} private static String verdictLabel(Verdict verdict){return switch(verdict){case CORRECT->"Correct";case PARTIAL->"Partial";case INCORRECT->"Incorrect";case SKIPPED->"Skipped";};}
}
