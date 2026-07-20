package dev.codedefense.passport;
import dev.codedefense.domain.*;
import java.io.PrintWriter;
import java.util.Objects;
public final class ChangeHandoffTerminalRenderer {
 public void render(DecodedChangeHandoff decoded,PrintWriter out){Objects.requireNonNull(decoded);var h=decoded.handoff();var latest=h.attempts().getLast();out.println("Change Handoff");out.println("Integrity: "+decoded.integrity().name().toLowerCase());out.println("Mode: "+h.changeKind());out.println("Fingerprint: "+h.diffFingerprint().substring(0,12));out.println("Attempts: "+h.attempts().size());out.println("Latest overall score: "+latest.overallScore()+"/100");out.println("Readiness: "+latest.readiness().displayName());for(var c:latest.categories())out.println("- "+c.id()+": "+c.finalScore()+"/100");out.println("Integrity is a corruption check, not proof of authorship.");out.flush();}
 public void renderMatch(HandoffMatchStatus status,PrintWriter out){out.println("Local change match: "+status);out.flush();}
}
