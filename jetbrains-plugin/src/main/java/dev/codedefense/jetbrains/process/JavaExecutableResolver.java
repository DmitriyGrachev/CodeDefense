package dev.codedefense.jetbrains.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

public final class JavaExecutableResolver {
    public Path resolve(Path configuredJavaHome) {
        if (configuredJavaHome == null) {
            throw invalidJavaHome();
        }
        try {
            Path root = configuredJavaHome.toAbsolutePath().normalize();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw invalidJavaHome();
            }
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            String executableName = System.getProperty("os.name", "")
                    .toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
            Path executable = realRoot.resolve("bin").resolve(executableName).normalize();
            Path parent = executable.getParent().toRealPath();
            if (!parent.startsWith(realRoot) || Files.isSymbolicLink(executable)
                    || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)) {
                throw invalidJavaHome();
            }
            Path realExecutable = executable.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realExecutable.startsWith(realRoot)) {
                throw invalidJavaHome();
            }
            return realExecutable;
        } catch (IOException exception) {
            throw new BridgeTransportException("The configured Java runtime is unavailable.", exception);
        }
    }

    private BridgeTransportException invalidJavaHome() {
        return new BridgeTransportException("The configured Java runtime is unavailable.");
    }
}
