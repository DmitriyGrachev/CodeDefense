package dev.codedefense.report;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public final class ReportNarrativeSchemaLoader {
    private static final String RESOURCE="schemas/report-narrative.schema.json"; private static final int MAX_BYTES=256*1024;
    private final ClassLoader classLoader; private final ObjectMapper mapper=new ObjectMapper(); private volatile String cached;
    public ReportNarrativeSchemaLoader(){this(ReportNarrativeSchemaLoader.class.getClassLoader());} ReportNarrativeSchemaLoader(ClassLoader classLoader){this.classLoader=Objects.requireNonNull(classLoader);}
    public String load(){String value=cached; if(value!=null)return value; synchronized(this){if(cached==null) cached=loadValidated(); return cached;}}
    private String loadValidated(){try(InputStream in=classLoader.getResourceAsStream(RESOURCE)){if(in==null)throw unavailable(); String value=normalize(decode(read(in))); JsonNode root=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value); validate(root); return value;}catch(IOException|RuntimeException e){throw unavailable();}}
    private static void validate(JsonNode root){if(root==null||!root.isObject()||root.path("additionalProperties").asBoolean(true)||!"object".equals(root.path("type").asText()))throw unavailable(); Set<String> rootFields=new HashSet<>();root.fieldNames().forEachRemaining(rootFields::add);if(!rootFields.equals(Set.of("type","additionalProperties","required","properties")))throw unavailable(); Set<String> fields=new HashSet<>(); root.path("properties").fieldNames().forEachRemaining(fields::add); Set<String> required=new HashSet<>(); root.path("required").forEach(n->required.add(n.asText())); Set<String> expected=Set.of("headline","summary","strengths","knowledgeGaps","recommendedActions"); if(!fields.equals(expected)||!required.equals(expected)||containsUnsupported(root))throw unavailable();}
    private static boolean containsUnsupported(JsonNode node){if(node.isObject()){Iterator<String> it=node.fieldNames();while(it.hasNext()){String name=it.next();if(Set.of("$ref","oneOf","anyOf","allOf","not","if","then","else","nullable","uniqueItems").contains(name))return true;if(containsUnsupported(node.get(name)))return true;}}else if(node.isArray())for(JsonNode child:node)if(containsUnsupported(child))return true;return false;}
    private static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){if(out.size()+n>MAX_BYTES)throw unavailable();out.write(b,0,n);}return out.toByteArray();}
    private static String decode(byte[] b)throws CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();} private static String normalize(String v){return v.replace("\r\n","\n").replace('\r','\n');} private static IllegalStateException unavailable(){return new IllegalStateException("Report narrative schema resource is unavailable.");}
}
