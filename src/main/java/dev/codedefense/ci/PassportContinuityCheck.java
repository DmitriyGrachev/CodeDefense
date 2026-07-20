package dev.codedefense.ci;

import java.nio.file.Path;

@FunctionalInterface
public interface PassportContinuityCheck {
    PassportContinuityResult check(Path repository, String base, String head);
}
