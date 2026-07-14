package dev.codedefense.scanner;

import dev.codedefense.domain.SourceFile;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FilePrioritizer {
    public List<SourceFile> prioritize(List<SourceFile> files) {
        return files.stream().sorted(Comparator.comparingInt(this::score).reversed()
                .thenComparing(file -> normalized(file.relativePath()))).toList();
    }

    public int score(SourceFile file) {
        String path = normalized(file.relativePath()).toLowerCase(Locale.ROOT);
        String name = path.substring(path.lastIndexOf('/') + 1);
        int base = category(path, name);
        int score = base;
        if (file.sizeBytes() > 16 * 1024) score -= 15;
        if (file.relativePath().getNameCount() > 8) score -= 10;
        return score;
    }

    private int category(String path, String name) {
        if (name.equals("readme") || name.equals("readme.md") || name.equals("pom.xml") || name.startsWith("build.gradle")
                || name.startsWith("settings.gradle") || name.equals("package.json") || name.equals("pyproject.toml")) return 100;
        if (path.contains("src/test/") || name.endsWith("test.java") || name.endsWith("test.ts")) return 20;
        if (name.endsWith("application.java") || name.endsWith("application.kt") || name.equals("main.java")) return 90;
        if (contains(path, "controller", "route", "endpoint")) return 70;
        if (contains(path, "service", "usecase", "scheduler", "worker", "processor")) return 65;
        if (contains(path, "repository", "persistence", "client", "integration", "gateway")) return 55;
        if (contains(path, "config", "configuration") || name.startsWith("application.")) return 50;
        if (contains(path, "domain", "model", "entity", "dto")) return 35;
        return 0;
    }

    private boolean contains(String path, String... terms) {
        for (String term : terms) if (path.contains(term)) return true;
        return false;
    }

    private String normalized(java.nio.file.Path path) {
        return path.toString().replace('\\', '/');
    }
}
