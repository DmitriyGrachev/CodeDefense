package dev.codedefense.domain;

import java.util.List;
import java.util.Objects;

public record ProjectComponent(String name, String kind, String responsibility, List<String> paths) {
    public ProjectComponent {
        name = CodeEvidence.requireNonBlank(name, "name");
        kind = CodeEvidence.requireNonBlank(kind, "kind");
        if (kind.length() > 64) {
            throw new IllegalArgumentException("kind is too long");
        }
        responsibility = CodeEvidence.requireNonBlank(responsibility, "responsibility");
        Objects.requireNonNull(paths, "paths");
        if (paths.size() < 1 || paths.size() > 5) {
            throw new IllegalArgumentException("paths must contain between one and five entries");
        }
        paths = CodeEvidence.copyNonBlankStrings(paths, "paths");
    }
}
