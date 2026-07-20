package dev.codedefense.change;

import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.GitChangeIdentity;
import java.nio.file.Path;

public interface GitChangeSource {
    CapturedGitChange capture(Path requestedPath, ChangeSelector selector);
    GitChangeIdentity captureIdentity(Path requestedPath, ChangeSelector selector);
}
