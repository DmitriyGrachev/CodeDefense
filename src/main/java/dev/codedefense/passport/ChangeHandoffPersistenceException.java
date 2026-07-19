package dev.codedefense.passport;
public final class ChangeHandoffPersistenceException extends RuntimeException {
 private ChangeHandoffPersistenceException(String message) { super(message); }
 public static ChangeHandoffPersistenceException invalid() { return new ChangeHandoffPersistenceException("Unable to read the Change Handoff."); }
 public static ChangeHandoffPersistenceException writeFailure() { return new ChangeHandoffPersistenceException("Unable to write the Change Handoff."); }
}
