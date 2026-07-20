package dev.codedefense.application;

import java.util.Objects;

public record CodexProvenanceRequest(boolean enabled, String threadId) {
    public CodexProvenanceRequest {
        Objects.requireNonNull(threadId, "threadId");
        if (enabled && (threadId.isBlank() || threadId.length() > 512
                || threadId.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("thread ID is invalid");
        }
        if (!enabled && !threadId.isEmpty()) {
            throw new IllegalArgumentException("disabled provenance cannot retain a thread ID");
        }
    }
    public static CodexProvenanceRequest disabled() { return new CodexProvenanceRequest(false, ""); }
    public static CodexProvenanceRequest enabled(String threadId) { return new CodexProvenanceRequest(true, threadId); }
    @Override public String toString() {
        return "CodexProvenanceRequest[enabled=%s, threadIdLength=%d]".formatted(enabled, threadId.length());
    }
}
