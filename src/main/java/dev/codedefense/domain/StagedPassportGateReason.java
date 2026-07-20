package dev.codedefense.domain;

public enum StagedPassportGateReason {
    NONE,
    NO_INDEX_ENTRIES,
    NO_STAGED_HISTORY,
    IDENTITY_MATCH,
    IDENTITY_CHANGED,
    INVALID_REPOSITORY,
    GIT_CAPTURE_FAILED,
    PASSPORT_STORE_FAILED
}
