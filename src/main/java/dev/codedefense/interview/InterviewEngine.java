package dev.codedefense.interview;
import dev.codedefense.domain.*; import dev.codedefense.terminal.*;
import java.util.*;
public final class InterviewEngine implements InterviewRunner {
 private final AnswerEvaluator evaluator; private final UserInput input; private final InterviewOutput output;
 private final InterviewScorer scorer; private final ReadinessClassifier classifier; private final InterviewConfig config;
 public InterviewEngine(AnswerEvaluator evaluator,UserInput input,InterviewOutput output,InterviewScorer scorer,ReadinessClassifier classifier,InterviewConfig config){this.evaluator=Objects.requireNonNull(evaluator);this.input=Objects.requireNonNull(input);this.output=Objects.requireNonNull(output);this.scorer=Objects.requireNonNull(scorer);this.classifier=Objects.requireNonNull(classifier);this.config=Objects.requireNonNull(config);}
 @Override public InterviewSession conduct(ProjectAnalysis analysis){
  Objects.requireNonNull(analysis); int total=config.primaryQuestionCount(); if(analysis.questions().size()!=total)throw new IllegalArgumentException("Project analysis question count does not match the interview configuration");
  output.renderIntroduction(total); List<QuestionResult> results=new ArrayList<>(total); int skipped=0;
  for(int index=0;index<total;index++){
   TechnicalQuestion question=analysis.questions().get(index); int number=index+1; output.renderPrimaryQuestion(number,total,question);
   String primaryAnswer=readValid(); AnswerEvaluation primaryEvaluation;
   if(isSkip(primaryAnswer)){primaryEvaluation=AnswerEvaluation.skipped();output.renderSkipped(false);skipped++;}
   else{output.renderEvaluating();primaryEvaluation=evaluator.evaluate(request(analysis,question,EvaluationStage.PRIMARY,primaryAnswer,question.prompt(),primaryAnswer,Optional.empty()));output.renderEvaluation(primaryEvaluation);}
   InterviewTurn primary=new InterviewTurn(TurnType.PRIMARY,question.prompt(),primaryAnswer,primaryEvaluation); Optional<InterviewTurn> followUp=Optional.empty();
   if(primaryEvaluation.verdict()!=Verdict.SKIPPED&&primaryEvaluation.verdict()!=Verdict.CORRECT&&primaryEvaluation.followUpQuestion().isPresent()){
    String prompt=primaryEvaluation.followUpQuestion().orElseThrow(); output.renderFollowUp(prompt); String answer=readValid(); AnswerEvaluation evaluation;
    if(isSkip(answer)){evaluation=AnswerEvaluation.skipped();output.renderSkipped(true);}else{output.renderEvaluating();evaluation=evaluator.evaluate(request(analysis,question,EvaluationStage.FOLLOW_UP,primaryAnswer,prompt,answer,Optional.of(primaryEvaluation)));output.renderEvaluation(evaluation);}
    followUp=Optional.of(new InterviewTurn(TurnType.FOLLOW_UP,prompt,answer,evaluation));
   }
   int score=scorer.calculateQuestionScore(primary,followUp); QuestionResult result=new QuestionResult(number,question,primary,followUp,score);results.add(result);output.renderQuestionScore(number,score);
  }
  int overall=scorer.calculateOverallScore(results);InterviewSession session=new InterviewSession(analysis.projectName(),results,overall,classifier.classify(overall),skipped);output.renderSummary(session);return session;
 }
 private String readValid(){for(;;){String answer=input.readAnswer("> ");if(answer==null)throw new InterviewCancelledException("Session cancelled. No report was generated.");answer=answer.strip();if(answer.isBlank()){output.renderInputValidationError("Answer cannot be blank. Enter skip to skip.");continue;}if(answer.length()>config.maximumAnswerCharacters()){output.renderInputValidationError("Answer exceeds 8000 characters.");continue;}return answer;}}
 private static boolean isSkip(String value){return "skip".equalsIgnoreCase(value.strip());}
 private static AnswerEvaluationRequest request(ProjectAnalysis a,TechnicalQuestion q,EvaluationStage s,String primary,String prompt,String current,Optional<AnswerEvaluation> previous){return new AnswerEvaluationRequest(a.projectName(),a.projectType(),a.summary(),q,s,primary,prompt,current,previous);}
}
