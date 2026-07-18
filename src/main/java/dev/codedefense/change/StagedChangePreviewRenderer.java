package dev.codedefense.change;

import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.StagedFileStatus;
import java.io.PrintWriter;
import java.util.Objects;

/** Renders safe staged-change metadata before any source content can be sent. */
public final class StagedChangePreviewRenderer {
    private final CodeDefenseConfig config;

    public StagedChangePreviewRenderer() {
        this(CodeDefenseConfig.defaults());
    }

    StagedChangePreviewRenderer(CodeDefenseConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void render(StagedChange change, ProjectSnapshot snapshot, PrintWriter out) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(out, "out");
        long added = change.files().stream().filter(file -> file.status() == StagedFileStatus.ADDED).count();
        long deleted = change.files().stream().filter(file -> file.status() == StagedFileStatus.DELETED).count();
        long changed = change.files().size() - added - deleted;
        long truncated = snapshot.selectedFiles().stream().filter(ProjectSnapshot.SelectedFile::truncated).count();

        out.println("Repository: " + snapshot.projectName());
        out.println("Mode: Staged change");
        out.println("Base commit: " + shortId(change.baseCommit()));
        out.println("Index tree: " + shortId(change.indexTree()));
        out.println("Fingerprint: " + shortId(change.diffFingerprint()));
        out.println("Changed: " + changed + ", added: " + added + ", deleted: " + deleted);
        out.println("Unstaged working-tree content ignored: yes");
        out.println("Selected-file limit: " + config.maximumSelectedFiles());
        out.println("Snapshot byte limit: " + config.maximumSnapshotBytes());
        out.println("Selected files: " + snapshot.selectedFiles().size() + " / " + config.maximumSelectedFiles());
        out.println("Snapshot bytes: " + snapshot.promptBytes() + " / " + config.maximumSnapshotBytes());
        out.println("Truncated files: " + truncated);
        out.println("Redactions: " + snapshot.redactionCount());
        out.println("Selected relative paths:");
        snapshot.selectedFiles().forEach(file -> out.println("- " + file.relativePath().toString().replace('\\', '/')));
        out.flush();
    }

    private String shortId(String value) {
        return value.substring(0, Math.min(12, value.length()));
    }
}
