package dev.codedefense.change;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedFileStatus;
import java.io.PrintWriter;
import java.util.Objects;

public final class GitChangePreviewRenderer {
    private final CodeDefenseConfig config;
    public GitChangePreviewRenderer() { this(CodeDefenseConfig.defaults()); }
    GitChangePreviewRenderer(CodeDefenseConfig config) { this.config = Objects.requireNonNull(config); }

    public void render(GitChange change, ProjectSnapshot snapshot, PrintWriter out) {
        long added = change.files().stream().filter(f -> f.status() == StagedFileStatus.ADDED).count();
        long deleted = change.files().stream().filter(f -> f.status() == StagedFileStatus.DELETED).count();
        long truncated = snapshot.selectedFiles().stream().filter(ProjectSnapshot.SelectedFile::truncated).count();
        out.println("Repository: " + snapshot.projectName());
        out.println("Mode: " + switch (change.kind()) { case STAGED -> "Staged change"; case COMMIT -> "Commit"; case RANGE -> "Range"; });
        out.println("Base commit: " + shortId(change.baseCommit()));
        out.println("Target identity: " + shortId(change.targetIdentity()));
        out.println("Fingerprint: " + shortId(change.diffFingerprint()));
        out.println("Changed: " + (change.files().size() - added - deleted) + ", added: " + added + ", deleted: " + deleted);
        out.println("Unstaged working-tree content ignored: yes");
        out.println("Selected-file limit: " + config.maximumSelectedFiles());
        out.println("Snapshot byte limit: " + config.maximumSnapshotBytes());
        out.println("Selected files: " + snapshot.selectedFiles().size() + " / " + config.maximumSelectedFiles());
        out.println("Snapshot bytes: " + snapshot.promptBytes() + " / " + config.maximumSnapshotBytes());
        out.println("Truncated files: " + truncated);
        out.println("Redactions: " + snapshot.redactionCount());
        out.println("Selected relative paths:");
        snapshot.selectedFiles().forEach(f -> out.println("- " + f.relativePath().toString().replace('\\', '/')));
        out.flush();
    }
    private static String shortId(String value) { return value.substring(0, Math.min(12, value.length())); }
}
