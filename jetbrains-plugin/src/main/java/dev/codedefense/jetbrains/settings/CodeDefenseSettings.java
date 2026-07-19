package dev.codedefense.jetbrains.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

@Service(Service.Level.APP)
@State(name = "CodeDefenseSettings", storages = @Storage("CodeDefense.xml"))
public final class CodeDefenseSettings implements PersistentStateComponent<CodeDefenseSettings.State> {
    private State state = new State();

    public static CodeDefenseSettings getInstance() {
        return ApplicationManager.getApplication().getService(CodeDefenseSettings.class);
    }

    @Override public State getState() { return state.copy(); }

    @Override public void loadState(State incoming) {
        State candidate = incoming == null ? new State() : incoming.copy();
        if (!List.of("STAGED", "COMMIT", "RANGE").contains(candidate.defaultSelector)) {
            candidate.defaultSelector = "STAGED";
        }
        if (!List.of("balanced", "architecture", "failure-modes", "testing")
                .contains(candidate.defaultFocus)) {
            candidate.defaultFocus = "balanced";
        }
        state = candidate;
    }

    public void update(boolean useBundledCli, String cliJarOverride, String selector, String focus) {
        State candidate = new State();
        candidate.useBundledCli = useBundledCli;
        candidate.cliJarOverride = cliJarOverride == null ? "" : cliJarOverride.trim();
        candidate.defaultSelector = selector;
        candidate.defaultFocus = focus;
        if (!useBundledCli) validateOverride(Path.of(candidate.cliJarOverride));
        loadState(candidate);
    }

    public Path resolveCliJar(Path bundledJar) {
        return state.useBundledCli ? bundledJar.toAbsolutePath().normalize()
                : validateOverride(Path.of(state.cliJarOverride));
    }

    public static Path validateOverride(Path path) {
        if (path == null || !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("Select an existing CodeDefense JAR.");
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Select an existing regular non-symlink CodeDefense JAR.");
            }
            return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Select an existing CodeDefense JAR.", exception);
        }
    }

    public static final class State {
        public boolean useBundledCli = true;
        public String cliJarOverride = "";
        public String defaultSelector = "STAGED";
        public String defaultFocus = "balanced";

        State copy() {
            State copy = new State();
            copy.useBundledCli = useBundledCli;
            copy.cliJarOverride = cliJarOverride;
            copy.defaultSelector = defaultSelector;
            copy.defaultFocus = defaultFocus;
            return copy;
        }

        @Override public String toString() {
            return "CodeDefenseSettings.State[useBundledCli=" + useBundledCli
                    + ", defaultSelector=" + defaultSelector + ", defaultFocus=" + defaultFocus + "]";
        }
    }
}
