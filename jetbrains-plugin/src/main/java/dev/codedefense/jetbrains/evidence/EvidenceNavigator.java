package dev.codedefense.jetbrains.evidence;

import dev.codedefense.jetbrains.process.EvidenceLocationView;
import java.util.Objects;

/** Opens validated evidence without exposing source text to the caller. */
public interface EvidenceNavigator {
    NavigationResult open(EvidenceLocationView location);

    enum NavigationStatus {
        OPENED,
        UNSAFE,
        UNAVAILABLE,
        STALE
    }

    record NavigationResult(NavigationStatus status, String message) {
        public NavigationResult {
            Objects.requireNonNull(status, "status");
            message = Objects.requireNonNull(message, "message");
            if (message.isBlank()) throw new IllegalArgumentException("Navigation message must not be blank.");
        }

        public boolean opened() {
            return status == NavigationStatus.OPENED;
        }
    }
}
