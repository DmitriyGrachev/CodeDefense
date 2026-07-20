package dev.codedefense.change;

import dev.codedefense.domain.StagedChangeIdentity;
import dev.codedefense.domain.StagedChange;
import java.nio.file.Path;

/** Captures the exact staged Git index state without reading the working tree. */
public interface StagedChangeSource {
    CapturedStagedChange capture(Path requestedPath);

    default StagedChangeIdentity captureIdentity(Path requestedPath) {
        return StagedChangeIdentity.from(capture(requestedPath).change());
    }

    StagedChange inspect(Path requestedPath);
}
