package dev.codedefense.report;

import dev.codedefense.domain.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;

final class Fixtures {
    static ReportNarrative narrative() { return narrative(List.of("Explains project flow"), List.of("Error handling"), List.of("Practice error handling")); }
    static ReportNarrative narrative(List<String> s, List<String> g, List<String> a) { return new ReportNarrative("Useful report headline", "A sufficiently detailed summary that does not claim a score.", s, g, a); }
    static ReportMetadata metadata() { return new ReportMetadata(Instant.EPOCH, "gpt-5.6-terra", "project-name", "Java CLI", List.of(new AnalyzedFile("src/App.java", 2, false, 20)), 20, 0); }
    static ReportGenerationRequest request() { return request("Question prompt", "answer", "key", "reason"); }
    static ReportGenerationRequest request(String prompt, String answer, String key, String reason) {
        TechnicalQuestion q1=q("one", prompt, key, reason), q2=q("two", "Second question", key, reason), q3=q("three", "Third question", key, reason);
        ProjectAnalysis analysis = new ProjectAnalysis("project-name", "Java CLI", "A project suitable for report narrative testing.", List.of("Start command", "Finish command"), List.of(new ProjectComponent("App", "entry", "Starts the project.", List.of("src/App.java"))), List.of("flow", "errors"), List.of(q1,q2,q3));
        List<QuestionResult> results=new ArrayList<>(); for (int i=0;i<3;i++) { TechnicalQuestion q=analysis.questions().get(i); AnswerEvaluation e=new AnswerEvaluation(Verdict.PARTIAL,60,"Useful feedback.",List.of("flow"),List.of("errors"),Optional.empty()); results.add(new QuestionResult(i+1,q,new InterviewTurn(TurnType.PRIMARY,q.prompt(),answer,e),Optional.empty(),60)); }
        return new ReportGenerationRequest(analysis,new InterviewSession("project-name",results,60,Readiness.REVIEW_NEEDED,0));
    }
    static ReportGenerationRequest requestWithEvidencePath(String evidencePath) {
        TechnicalQuestion q1=q("one", "Question prompt", "key", "reason", evidencePath), q2=q("two", "Second question", "key", "reason", evidencePath), q3=q("three", "Third question", "key", "reason", evidencePath);
        ProjectAnalysis analysis = new ProjectAnalysis("project-name", "Java CLI", "A project suitable for report narrative testing.", List.of("Start command", "Finish command"), List.of(new ProjectComponent("App", "entry", "Starts the project.", List.of("src/App.java"))), List.of("flow", "errors"), List.of(q1,q2,q3));
        List<QuestionResult> results=new ArrayList<>(); for (int i=0;i<3;i++) { TechnicalQuestion q=analysis.questions().get(i); AnswerEvaluation e=new AnswerEvaluation(Verdict.PARTIAL,60,"Useful feedback.",List.of("flow"),List.of("errors"),Optional.empty()); results.add(new QuestionResult(i+1,q,new InterviewTurn(TurnType.PRIMARY,q.prompt(),"answer",e),Optional.empty(),60)); }
        return new ReportGenerationRequest(analysis,new InterviewSession("project-name",results,60,Readiness.REVIEW_NEEDED,0));
    }
    static ReportGenerationRequest maliciousRequest(String injected) {
        TechnicalQuestion q1=q("one", "question " + injected, "private-key", "private-reason"), q2=q("two", "Second question", "private-key", "private-reason"), q3=q("three", "Third question", "private-key", "private-reason");
        ProjectAnalysis analysis = new ProjectAnalysis("project-name", "Java CLI", "summary " + injected, List.of("Start command", "Finish command"), List.of(new ProjectComponent("App", "entry", "Starts the project.", List.of("src/App.java"))), List.of("flow", "errors"), List.of(q1,q2,q3));
        List<QuestionResult> results = new ArrayList<>();
        for (int i=0;i<3;i++) { TechnicalQuestion q=analysis.questions().get(i); AnswerEvaluation e=new AnswerEvaluation(Verdict.PARTIAL,60,"feedback " + injected,List.of("concept " + injected),List.of("missing " + injected),Optional.of("follow-up " + injected)); results.add(new QuestionResult(i+1,q,new InterviewTurn(TurnType.PRIMARY,q.prompt(),"private-answer",e),Optional.empty(),60)); }
        return new ReportGenerationRequest(analysis,new InterviewSession("project-name",results,60,Readiness.REVIEW_NEEDED,0));
    }
    private static TechnicalQuestion q(String id,String prompt,String key,String reason) { return q(id, prompt, key, reason, "src/App.java"); }
    private static TechnicalQuestion q(String id,String prompt,String key,String reason,String evidencePath) { return new TechnicalQuestion(id,prompt,"Understand project behavior.",List.of(key,"Second key point"),List.of(new CodeEvidence(evidencePath,1,2,reason))); }
}
