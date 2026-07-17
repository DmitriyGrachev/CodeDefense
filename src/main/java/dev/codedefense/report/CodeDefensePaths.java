package dev.codedefense.report;

import java.nio.file.Path;
import java.util.Objects;

public record CodeDefensePaths(Path rootDirectory, Path reportsDirectory, Path latestPointer) {
    public CodeDefensePaths {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        Objects.requireNonNull(reportsDirectory, "reportsDirectory");
        Objects.requireNonNull(latestPointer, "latestPointer");
        if (!rootDirectory.isAbsolute() || !rootDirectory.equals(rootDirectory.normalize())
                || !reportsDirectory.isAbsolute() || !reportsDirectory.equals(reportsDirectory.normalize())
                || !latestPointer.isAbsolute() || !latestPointer.equals(latestPointer.normalize())
                || !reportsDirectory.startsWith(rootDirectory) || !latestPointer.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Report paths must be normalized and contained by the report root");
        }
    }

    public static CodeDefensePaths under(Path userHome) {
        Objects.requireNonNull(userHome, "userHome");
        Path normalizedHome = userHome.toAbsolutePath().normalize();
        Path root = normalizedHome.resolve(".codedefense").normalize();
        Path reports = root.resolve("reports").normalize();
        Path pointer = root.resolve("latest-report.txt").normalize();
        if (!root.startsWith(normalizedHome) || !reports.startsWith(root) || !pointer.startsWith(root)) {
            throw new IllegalArgumentException("Report paths escape the user home");
        }
        return new CodeDefensePaths(root, reports, pointer);
    }

    public static CodeDefensePaths defaults() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            throw ReportPersistenceException.saveFailure();
        }
        return under(Path.of(userHome));
    }
}
