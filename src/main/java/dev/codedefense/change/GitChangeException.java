package dev.codedefense.change;

/** A fixed, content-free failure from the staged Git boundary. */
public final class GitChangeException extends RuntimeException {
    public enum Kind { INVALID_REPOSITORY, NO_HEAD, NO_STAGED_CHANGE, EXECUTION_FAILED, MALFORMED_DATA }

    private final Kind kind;

    public GitChangeException(Kind kind) {
        super(message(kind));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    private static String message(Kind kind) {
        return switch (kind) {
            case INVALID_REPOSITORY -> "The supplied path is not a readable Git repository.";
            case NO_HEAD -> "The Git repository has no committed HEAD.";
            case NO_STAGED_CHANGE -> "No staged Git changes were found.";
            case EXECUTION_FAILED -> "Git could not safely capture the staged change.";
            case MALFORMED_DATA -> "Git returned invalid staged change data.";
        };
    }
}
