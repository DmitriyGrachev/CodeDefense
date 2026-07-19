package dev.codedefense.application;

import dev.codedefense.domain.PassportVerification;
import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface ChangePassportVerifier {
    Optional<PassportVerification> verify(Path repositoryPath);
}
