package dev.codedefense.domain;
public final class EmptyProjectSnapshotException extends RuntimeException { public EmptyProjectSnapshotException() { super("No useful source content fits in the project snapshot."); } }
