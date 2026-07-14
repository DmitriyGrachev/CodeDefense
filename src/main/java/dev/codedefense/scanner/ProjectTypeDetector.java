package dev.codedefense.scanner;

import dev.codedefense.domain.ScanSummary;
import java.nio.file.Path;
import java.util.Set;

public final class ProjectTypeDetector {
    private static final int MANIFEST_LIMIT = 16 * 1024;
    private final BoundedTextFileReader reader;
    public ProjectTypeDetector() { this(new BoundedTextFileReader()); }
    ProjectTypeDetector(BoundedTextFileReader reader) { this.reader = reader; }
    public String detect(ScanSummary summary) {
        Set<String> names = summary.candidates().stream().map(file -> file.relativePath().getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        String pom = read(summary.root().resolve("pom.xml"));
        if (names.contains("pom.xml")) return pom.contains("spring-boot") ? "Java / Spring Boot" : "Java / Maven";
        if (names.contains("build.gradle") || names.contains("build.gradle.kts")) return "Java / Gradle";
        String pkg = read(summary.root().resolve("package.json"));
        if (pkg.contains("\"next\"")) return "TypeScript / Next.js";
        if (pkg.contains("\"react\"")) return "TypeScript / React";
        if (names.stream().anyMatch(name -> name.endsWith(".ts") || name.endsWith(".tsx"))) return "TypeScript / Node.js";
        if (names.stream().anyMatch(name -> name.endsWith(".js") || name.endsWith(".jsx"))) return "JavaScript / Node.js";
        String requirements = read(summary.root().resolve("requirements.txt")) + read(summary.root().resolve("pyproject.toml"));
        if (requirements.toLowerCase().contains("fastapi")) return "Python / FastAPI";
        if (requirements.toLowerCase().contains("django")) return "Python / Django";
        if (requirements.toLowerCase().contains("flask")) return "Python / Flask";
        if (names.stream().anyMatch(name -> name.endsWith(".java"))) return "Java";
        if (names.stream().anyMatch(name -> name.endsWith(".py"))) return "Python";
        return "Generic";
    }
    private String read(Path path) { return reader.read(path.getParent(), path, MANIFEST_LIMIT).content(); }
}
