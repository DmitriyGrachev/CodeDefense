package dev.codedefense.passport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import dev.codedefense.domain.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public final class ChangeHandoffJsonCodec {
 public static final int MAXIMUM_BYTES = 1024 * 1024;
 private static final Set<String> ROOT = Set.of("schemaVersion","handoffId","createdAt","repositoryIdentityHash",
   "changeKind","baseCommit","sourceIdentity","diffFingerprint","attempts","payloadSha256");
 private static final Set<String> ATTEMPT = Set.of("attemptId","supersedes","attemptNumber","diffFingerprint",
   "createdAt","overallScore","readiness","categories");
 private static final Set<String> CATEGORY = Set.of("id","primaryVerdict","primaryScore","followUpVerdict","followUpScore","finalScore");
 private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
   .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

 public byte[] encode(ChangeHandoff handoff) {
  try {
   ObjectNode payload = payload(handoff); String checksum = hash(mapper.writeValueAsBytes(payload));
   payload.put("payloadSha256", checksum);
   byte[] json = mapper.writeValueAsBytes(payload); if (json.length + 1 > MAXIMUM_BYTES) throw ChangeHandoffPersistenceException.writeFailure();
   return (new String(json, StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
  } catch (ChangeHandoffPersistenceException e) { throw e; }
  catch (Exception e) { throw ChangeHandoffPersistenceException.writeFailure(); }
 }

 public DecodedChangeHandoff decode(byte[] input) {
  try {
   if (input == null || input.length == 0 || input.length > MAXIMUM_BYTES) throw ChangeHandoffPersistenceException.invalid();
   String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
     .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();
   JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
   exact(root, ROOT); List<PassportAttemptSummary> attempts = new ArrayList<>();
   JsonNode attemptNodes = required(root,"attempts"); if (!attemptNodes.isArray()) throw ChangeHandoffPersistenceException.invalid();
   for (JsonNode node : attemptNodes) {
    exact(node, ATTEMPT); List<PassportCategoryReceipt> categories = new ArrayList<>();
    JsonNode categoryNodes=required(node,"categories"); if(!categoryNodes.isArray()) throw ChangeHandoffPersistenceException.invalid();
    for(JsonNode c:categoryNodes){ exact(c,CATEGORY); JsonNode fv=required(c,"followUpVerdict"), fs=required(c,"followUpScore");
     categories.add(new PassportCategoryReceipt(string(c,"id"),Verdict.valueOf(string(c,"primaryVerdict")),integer(c,"primaryScore"),
       fv.isNull()?Optional.empty():Optional.of(Verdict.valueOf(text(fv))),fs.isNull()?Optional.empty():Optional.of(number(fs)),integer(c,"finalScore"))); }
    JsonNode parent=required(node,"supersedes");
    attempts.add(new PassportAttemptSummary(new PassportAttemptId(string(node,"attemptId")),
      parent.isNull()?Optional.empty():Optional.of(new PassportAttemptId(text(parent))),integer(node,"attemptNumber"),
      string(node,"diffFingerprint"),Instant.parse(string(node,"createdAt")),integer(node,"overallScore"),
      Readiness.valueOf(string(node,"readiness")),categories));
   }
   String supplied=string(root,"payloadSha256");
   ChangeHandoff handoff=new ChangeHandoff(integer(root,"schemaVersion"),string(root,"handoffId"),Instant.parse(string(root,"createdAt")),
     string(root,"repositoryIdentityHash"),ChangeKind.valueOf(string(root,"changeKind")),string(root,"baseCommit"),
     string(root,"sourceIdentity"),string(root,"diffFingerprint"),attempts,supplied);
   String expected=hash(mapper.writeValueAsBytes(payload(handoff)));
   boolean equal=MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII));
   return new DecodedChangeHandoff(handoff,equal?HandoffIntegrity.INTACT:HandoffIntegrity.CORRUPT);
  } catch(ChangeHandoffPersistenceException e){throw e;} catch(Exception e){throw ChangeHandoffPersistenceException.invalid();}
 }

 private ObjectNode payload(ChangeHandoff h){ ObjectNode r=mapper.createObjectNode(); r.put("schemaVersion",h.schemaVersion());r.put("handoffId",h.handoffId());r.put("createdAt",h.createdAt().toString());r.put("repositoryIdentityHash",h.repositoryIdentityHash());r.put("changeKind",h.changeKind().name());r.put("baseCommit",h.baseCommit());r.put("sourceIdentity",h.sourceIdentity());r.put("diffFingerprint",h.diffFingerprint());
  ArrayNode array=r.putArray("attempts"); for(var a:h.attempts()){ObjectNode n=array.addObject();n.put("attemptId",a.attemptId().value());if(a.supersedes().isPresent())n.put("supersedes",a.supersedes().orElseThrow().value());else n.putNull("supersedes");n.put("attemptNumber",a.attemptNumber());n.put("diffFingerprint",a.diffFingerprint());n.put("createdAt",a.createdAt().toString());n.put("overallScore",a.overallScore());n.put("readiness",a.readiness().name());ArrayNode cs=n.putArray("categories");for(var c:a.categories()){ObjectNode x=cs.addObject();x.put("id",c.id());x.put("primaryVerdict",c.primaryVerdict().name());x.put("primaryScore",c.primaryScore());if(c.followUpVerdict().isPresent()){x.put("followUpVerdict",c.followUpVerdict().orElseThrow().name());x.put("followUpScore",c.followUpScore().orElseThrow());}else{x.putNull("followUpVerdict");x.putNull("followUpScore");}x.put("finalScore",c.finalScore());}}
  return r; }
 private static void exact(JsonNode n,Set<String>s){if(n==null||!n.isObject())throw ChangeHandoffPersistenceException.invalid();Set<String>a=new HashSet<>();n.fieldNames().forEachRemaining(a::add);if(!a.equals(s))throw ChangeHandoffPersistenceException.invalid();}
 private static JsonNode required(JsonNode n,String f){JsonNode v=n.get(f);if(v==null)throw ChangeHandoffPersistenceException.invalid();return v;}
 private static String string(JsonNode n,String f){return text(required(n,f));} private static String text(JsonNode n){if(!n.isTextual())throw ChangeHandoffPersistenceException.invalid();return n.textValue();}
 private static int integer(JsonNode n,String f){return number(required(n,f));} private static int number(JsonNode n){if(!n.isIntegralNumber()||!n.canConvertToInt())throw ChangeHandoffPersistenceException.invalid();return n.intValue();}
 private static String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
}
