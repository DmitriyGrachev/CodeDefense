package dev.codedefense.passport;
import dev.codedefense.domain.ChangeHandoff;
import dev.codedefense.domain.HandoffIntegrity;
import java.util.Objects;
public record DecodedChangeHandoff(ChangeHandoff handoff, HandoffIntegrity integrity) {
 public DecodedChangeHandoff { Objects.requireNonNull(handoff); Objects.requireNonNull(integrity); }
}
