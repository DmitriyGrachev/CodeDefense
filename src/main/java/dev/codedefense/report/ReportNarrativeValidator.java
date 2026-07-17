package dev.codedefense.report;

import dev.codedefense.ai.exception.InvalidCodexResponseException;
import dev.codedefense.domain.ReportNarrative;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

public final class ReportNarrativeValidator {
    private static final Pattern SPACE=Pattern.compile("\\s+");
    private static final Pattern SCORE=Pattern.compile("(?i)\\b(overall|final)\\s+score\\b|\\b\\d{1,3}\\s*/\\s*100\\b|\\b\\d{1,3}\\s*%");
    public ReportNarrative validate(ReportNarrative narrative){try{if(narrative==null||hasScoreClaim(narrative))throw invalid(); Set<String> seen=new HashSet<>(); for(List<String> values:List.of(narrative.strengths(),narrative.knowledgeGaps(),narrative.recommendedActions()))for(String value:values)if(!seen.add(key(value)))throw invalid(); return narrative;}catch(InvalidCodexResponseException e){throw e;}catch(RuntimeException e){throw invalid();}}
    private static boolean hasScoreClaim(ReportNarrative n){return SCORE.matcher(n.headline()).find()||SCORE.matcher(n.summary()).find()||Stream.of(n.strengths(),n.knowledgeGaps(),n.recommendedActions()).flatMap(Collection::stream).anyMatch(v->SCORE.matcher(v).find());}
    private static String key(String value){return SPACE.matcher(value.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);} private static InvalidCodexResponseException invalid(){return new InvalidCodexResponseException("Codex returned an invalid report narrative.");}
}
