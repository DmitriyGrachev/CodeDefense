package dev.codedefense.passport;

import java.nio.file.Path;
import java.util.Objects;

/** Contained local paths used only for source-free change passports. */
public record ChangePassportPaths(Path rootDirectory, Path passportsDirectory, Path latestPointer) {
    public ChangePassportPaths {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        Objects.requireNonNull(passportsDirectory, "passportsDirectory");
        Objects.requireNonNull(latestPointer, "latestPointer");
        if (!rootDirectory.isAbsolute() || !rootDirectory.equals(rootDirectory.normalize())
                || !passportsDirectory.isAbsolute() || !passportsDirectory.equals(passportsDirectory.normalize())
                || !latestPointer.isAbsolute() || !latestPointer.equals(latestPointer.normalize())
                || !passportsDirectory.startsWith(rootDirectory) || !latestPointer.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Passport paths must be normalized and contained");
        }
    }

    public static ChangePassportPaths under(Path userHome) {
        Path home = Objects.requireNonNull(userHome, "userHome").toAbsolutePath().normalize();
        Path root = home.resolve(".codedefense").normalize();
        return new ChangePassportPaths(root, root.resolve("change-passports").normalize(),
                root.resolve("latest-change-passport.txt").normalize());
    }

    public static ChangePassportPaths defaults() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) throw ChangePassportPersistenceException.saveFailure();
        return under(Path.of(userHome));
    }
}
