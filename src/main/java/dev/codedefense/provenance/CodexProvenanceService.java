package dev.codedefense.provenance;

import dev.codedefense.change.CapturedGitChange;
import dev.codedefense.domain.CodexProvenanceSummary;
import java.nio.file.Path;

public interface CodexProvenanceService {
    CodexProvenanceSummary capture(Path repository, CapturedGitChange change, String threadId);
}
