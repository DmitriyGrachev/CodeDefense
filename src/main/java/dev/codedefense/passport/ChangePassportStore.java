package dev.codedefense.passport;

import dev.codedefense.domain.ChangePassport;
import java.nio.file.Path;
import java.util.Optional;

public interface ChangePassportStore {
    Path save(ChangePassport passport);
    Optional<StoredPassportIdentity> readLatestIdentity();
}
