package dev.codedefense.passport;

import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ChangeHandoffJsonCodecTest {
 @Test void roundTripsDeterministicallyAndDetectsTampering() {
  ChangeHandoffJsonCodec codec=new ChangeHandoffJsonCodec(); ChangeHandoff handoff=handoff();
  byte[] first=codec.encode(handoff), second=codec.encode(handoff); assertArrayEquals(first,second);
  assertEquals(HandoffIntegrity.INTACT,codec.decode(first).integrity());
  String text=new String(first,java.nio.charset.StandardCharsets.UTF_8).replace("\"overallScore\":50","\"overallScore\":51");
  assertEquals(HandoffIntegrity.CORRUPT,codec.decode(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).integrity());
 }
 @Test void rejectsTrailingTokensInvalidUtf8AndOversize() {
  ChangeHandoffJsonCodec codec=new ChangeHandoffJsonCodec(); byte[] valid=codec.encode(handoff());
  assertThrows(ChangeHandoffPersistenceException.class,()->codec.decode((new String(valid)+"{}").getBytes()));
  assertThrows(ChangeHandoffPersistenceException.class,()->codec.decode(new byte[]{(byte)0xc3,0x28}));
  assertThrows(ChangeHandoffPersistenceException.class,()->codec.decode(new byte[ChangeHandoffJsonCodec.MAXIMUM_BYTES+1]));
 }
 private ChangeHandoff handoff(){var categories=List.of(
  new PassportCategoryReceipt("decision",Verdict.PARTIAL,50,Optional.empty(),Optional.empty(),50),
  new PassportCategoryReceipt("counterfactual",Verdict.PARTIAL,50,Optional.empty(),Optional.empty(),50),
  new PassportCategoryReceipt("test-prediction",Verdict.PARTIAL,50,Optional.empty(),Optional.empty(),50));
  var attempt=new PassportAttemptSummary(new PassportAttemptId("11111111-1111-4111-8111-111111111111"),Optional.empty(),1,"d".repeat(64),Instant.EPOCH,50,Readiness.REVIEW_NEEDED,categories);
  return new ChangeHandoff(1,"22222222-2222-4222-8222-222222222222",Instant.EPOCH,"a".repeat(64),ChangeKind.STAGED,"b".repeat(40),"c".repeat(64),"d".repeat(64),List.of(attempt),"0".repeat(64));}
}
