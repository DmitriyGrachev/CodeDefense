package dev.codedefense.application;

public record CodeDefenseConfig(int maximumSelectedFiles, int maximumSnapshotBytes, int maximumFileBlockBytes) {
    public static final int DEFAULT_MAXIMUM_SELECTED_FILES = 30;
    public static final int DEFAULT_MAXIMUM_SNAPSHOT_BYTES = 120 * 1024;
    public static final int DEFAULT_MAXIMUM_FILE_BLOCK_BYTES = 24 * 1024;

    public CodeDefenseConfig {
        if (maximumSelectedFiles <= 0 || maximumSnapshotBytes <= 0 || maximumFileBlockBytes <= 0
                || maximumFileBlockBytes > maximumSnapshotBytes) {
            throw new IllegalArgumentException("Snapshot limits must be positive and file blocks cannot exceed the snapshot");
        }
    }

    public static CodeDefenseConfig defaults() {
        return new CodeDefenseConfig(DEFAULT_MAXIMUM_SELECTED_FILES, DEFAULT_MAXIMUM_SNAPSHOT_BYTES,
                DEFAULT_MAXIMUM_FILE_BLOCK_BYTES);
    }
}
