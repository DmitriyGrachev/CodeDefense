package dev.codedefense.change;

import java.nio.file.Path;

/** Captures the exact staged Git index state without reading the working tree. */
public interface StagedChangeSource {
    CapturedStagedChange capture(Path requestedPath);
}
