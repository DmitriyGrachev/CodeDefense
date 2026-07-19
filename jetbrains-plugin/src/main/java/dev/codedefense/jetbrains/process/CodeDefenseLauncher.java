package dev.codedefense.jetbrains.process;

import dev.codedefense.jetbrains.process.BridgeLineCodec.BridgeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public interface CodeDefenseLauncher {
    BridgeProcess launch(Path projectRoot, BridgeLaunchSpec spec, Consumer<BridgeMessage> eventConsumer);

    static CodeDefenseLauncher production(Path bundledJar) {
        return new JdkCodeDefenseLauncher(bundledJar, Path.of(System.getProperty("java.home")));
    }

    enum Selector {
        STAGED, COMMIT, RANGE
    }

    record BridgeLaunchSpec(Selector selector, String selectorValue, String focus, boolean dryRun,
            boolean provenanceRequested) {
        public BridgeLaunchSpec(Selector selector, String selectorValue, String focus, boolean dryRun) {
            this(selector, selectorValue, focus, dryRun, false);
        }
        public BridgeLaunchSpec {
            Objects.requireNonNull(selector, "selector");
            if (focus == null || !List.of("balanced", "architecture", "failure-modes", "testing")
                    .contains(focus.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Unknown defense focus.");
            }
            focus = focus.toLowerCase(Locale.ROOT);
            if (selector == Selector.STAGED && selectorValue != null) {
                throw new IllegalArgumentException("Staged selector accepts no value.");
            }
            if (selector != Selector.STAGED && (selectorValue == null || selectorValue.isBlank())) {
                throw new IllegalArgumentException("The selector value is required.");
            }
        }
    }
}

final class JdkCodeDefenseLauncher implements CodeDefenseLauncher {
    private static final Duration TERMINATION_GRACE = Duration.ofSeconds(2);
    private final Path bundledJar;
    private final Path javaExecutable;
    JdkCodeDefenseLauncher(Path bundledJar, Path javaHome) {
        this.bundledJar = regularFile(bundledJar, "The bundled CodeDefense CLI is unavailable.");
        this.javaExecutable = new JavaExecutableResolver().resolve(javaHome);
    }

    @Override
    public BridgeProcess launch(Path projectRoot, BridgeLaunchSpec spec, Consumer<BridgeMessage> eventConsumer) {
        Path workingDirectory = projectRoot(projectRoot);
        return new BridgeProcess(protocolVersion -> new ProcessBuilder(
                command(workingDirectory, spec, protocolVersion))
                .directory(workingDirectory.toFile()).start(), eventConsumer, TERMINATION_GRACE);
    }

    List<String> command(Path projectRoot, BridgeLaunchSpec spec) {
        return command(projectRoot, spec, 2);
    }

    List<String> command(Path projectRoot, BridgeLaunchSpec spec, int protocolVersion) {
        Path root = projectRoot(projectRoot);
        Objects.requireNonNull(spec, "spec");
        if (protocolVersion != 1 && protocolVersion != 2) {
            throw new IllegalArgumentException("Unsupported bridge protocol version.");
        }
        List<String> command = new ArrayList<>(List.of(javaExecutable.toString(), "-jar", bundledJar.toString(),
                "bridge", "prove", "--protocol", Integer.toString(protocolVersion)));
        switch (spec.selector()) {
            case STAGED -> command.add("--staged");
            case COMMIT -> {
                command.add("--commit");
                command.add(spec.selectorValue());
            }
            case RANGE -> {
                command.add("--range");
                command.add(spec.selectorValue());
            }
        }
        command.add("--focus");
        command.add(spec.focus());
        if (spec.dryRun()) {
            command.add("--dry-run");
        }
        if (spec.provenanceRequested()) {
            command.add("--experimental-codex-provenance");
        }
        command.add(root.toString());
        return List.copyOf(command);
    }

    private Path projectRoot(Path path) {
        if (path == null) {
            throw new BridgeTransportException("The IDE project directory is unavailable.");
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
                throw new BridgeTransportException("The IDE project directory is unavailable.");
            }
            return normalized.toRealPath();
        } catch (IOException exception) {
            throw new BridgeTransportException("The IDE project directory is unavailable.", exception);
        }
    }

    private static Path regularFile(Path path, String message) {
        if (path == null) {
            throw new BridgeTransportException(message);
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized) || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new BridgeTransportException(message);
            }
            return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new BridgeTransportException(message, exception);
        }
    }
}
