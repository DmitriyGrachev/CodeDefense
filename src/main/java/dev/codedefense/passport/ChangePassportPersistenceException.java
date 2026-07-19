package dev.codedefense.passport;

/** Fixed, source-free persistence failure used by the filesystem boundary. */
public final class ChangePassportPersistenceException extends RuntimeException {
    private ChangePassportPersistenceException(String message) { super(message); }
    public static ChangePassportPersistenceException saveFailure() { return new ChangePassportPersistenceException("Unable to save the Change Passport."); }
    public static ChangePassportPersistenceException readFailure() { return new ChangePassportPersistenceException("Unable to read the latest Change Passport."); }
    public static ChangePassportPersistenceException receiptReadFailure() { return new ChangePassportPersistenceException("Unable to read a Change Passport receipt."); }
}
