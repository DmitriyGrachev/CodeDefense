package dev.codedefense.domain;

public enum StagedPassportGateState {
    NO_STAGED_CHANGE,
    UNDEFENDED,
    CURRENT,
    EXPIRED,
    UNAVAILABLE
}
