package dev.codedefense.scanner;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class ProjectFileFilter {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "out", "dist", "coverage",
            "node_modules", "vendor", ".gradle", ".next", "generated", "generated-sources"
    );
    private static final Set<String> SUPPORTED_NAMES = Set.of(
            "README.md", "README", "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "package.json", "pyproject.toml", "requirements.txt", "application.yml",
            "application.yaml", "application.properties", "Dockerfile", "docker-compose.yml", "docker-compose.yaml"
    );
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".py", ".js", ".jsx", ".ts", ".tsx", ".yml", ".yaml",
            ".properties", ".toml", ".md"
    );
    private static final Set<String> EXCLUDED_NAMES = Set.of(".env", "package-lock.json", "yarn.lock", "pnpm-lock.yaml");
    private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
            ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".class", ".jar", ".war",
            ".zip", ".tar", ".gz", ".png", ".jpg", ".jpeg", ".gif", ".pdf"
    );

    public boolean isExcludedDirectory(Path directory) {
        return EXCLUDED_DIRECTORIES.contains(fileName(directory));
    }

    public boolean isSupportedFile(Path file) {
        String name = fileName(file);
        return SUPPORTED_NAMES.contains(name) || SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    public boolean isExcludedFile(Path file) {
        String name = fileName(file);
        String lowercaseName = name.toLowerCase(Locale.ROOT);
        return EXCLUDED_NAMES.contains(name)
                || lowercaseName.startsWith(".env.")
                || EXCLUDED_EXTENSIONS.stream().anyMatch(lowercaseName::endsWith);
    }

    private String fileName(Path path) {
        return path.getFileName().toString();
    }
}
