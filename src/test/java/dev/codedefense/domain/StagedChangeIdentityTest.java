package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StagedChangeIdentityTest {
    @Test
    void representsAnEmptyStagedIndexWithoutTreatingItAsAnInvalidRepository() {
        StagedChangeIdentity identity = new StagedChangeIdentity(
                Path.of(".").toAbsolutePath().normalize(),
                "a".repeat(64),
                "b".repeat(40),
                "c".repeat(64),
                "d".repeat(64),
                List.of());

        assertFalse(identity.hasStagedChanges());
        assertTrue(identity.changedPathHashes().isEmpty());
    }
}
