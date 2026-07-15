package dev.codedefense.ai;

import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexNotAuthenticatedException;
import dev.codedefense.ai.exception.CodexNotInstalledException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Checks that a local Codex executable can be launched and has an authenticated session.
 */
public final class CodexEnvironmentChecker implements CodexPreflight {
    private static final int MAXIMUM_VERSION_CHARACTERS = 256;

    private final ProcessExecutor processExecutor;
    private final CodexRuntimeConfig config;
    private final Map<String, String> environment;
    private final Path workingDirectory;
    private final List<CodexExecutable> candidates;

    public CodexEnvironmentChecker(
            ProcessExecutor processExecutor,
            CodexRuntimeConfig config,
            CodexProcessEnvironment processEnvironment,
            Map<String, String> sourceEnvironment,
            Path workingDirectory,
            String operatingSystem) {
        this(
                processExecutor,
                config,
                processEnvironment,
                sourceEnvironment,
                workingDirectory,
                candidatesFor(operatingSystem));
    }

    private CodexEnvironmentChecker(
            ProcessExecutor processExecutor,
            CodexRuntimeConfig config,
            CodexProcessEnvironment processEnvironment,
            Map<String, String> sourceEnvironment,
            Path workingDirectory,
            List<CodexExecutable> candidates) {
        this.processExecutor = Objects.requireNonNull(processExecutor, "Process executor");
        this.config = Objects.requireNonNull(config, "Codex runtime configuration");
        this.environment = Objects.requireNonNull(processEnvironment, "Codex process environment")
                .sanitize(sourceEnvironment);
        this.workingDirectory = requireExistingDirectory(workingDirectory);
        this.candidates = List.copyOf(candidates);
    }

    /**
     * Creates the production preflight checker from the machine's current environment.
     */
    public static CodexEnvironmentChecker forCurrentEnvironment(
            ProcessExecutor processExecutor,
            CodexRuntimeConfig config,
            CodexProcessEnvironment processEnvironment,
            Path workingDirectory) {
        return new CodexEnvironmentChecker(
                processExecutor,
                config,
                processEnvironment,
                System.getenv(),
                workingDirectory,
                System.getProperty("os.name", ""));
    }

    @Override
    public CodexEnvironment checkReady() {
        CodexExecutionException lastExecutionFailure = null;

        for (CodexExecutable candidate : candidates) {
            ProcessResult versionResult;
            try {
                versionResult = processExecutor.execute(specification(candidate.commandPrefix(), List.of("--version")));
            } catch (ProcessStartException exception) {
                continue;
            } catch (RuntimeException exception) {
                throw startFailure(exception);
            }

            if (versionResult.timedOut()) {
                throw new CodexTimeoutException();
            }
            if (versionResult.exitCode() != 0) {
                lastExecutionFailure = new CodexExecutionException(
                        versionResult.exitCode(), "Codex version check returned a nonzero exit code.");
                continue;
            }

            String version = extractVersion(versionResult);
            verifyAuthentication(candidate);
            return new CodexEnvironment(candidate, version);
        }

        if (lastExecutionFailure != null) {
            throw lastExecutionFailure;
        }
        throw new CodexNotInstalledException();
    }

    private void verifyAuthentication(CodexExecutable executable) {
        ProcessResult loginResult;
        try {
            loginResult = processExecutor.execute(specification(executable.commandPrefix(), List.of("login", "status")));
        } catch (RuntimeException exception) {
            throw startFailure(exception);
        }

        if (loginResult.timedOut()) {
            throw new CodexTimeoutException();
        }
        if (loginResult.exitCode() != 0) {
            throw new CodexNotAuthenticatedException();
        }
    }

    private ProcessSpec specification(List<String> commandPrefix, List<String> arguments) {
        List<String> command = new ArrayList<>(commandPrefix);
        command.addAll(arguments);
        return new ProcessSpec(
                command,
                workingDirectory,
                environment,
                "",
                config.environmentCheckTimeout(),
                config.terminationGracePeriod(),
                config.maximumCapturedStdoutBytes(),
                config.maximumCapturedStderrBytes());
    }

    private static String extractVersion(ProcessResult result) {
        String version = firstNonblankLine(result.stdout());
        if (version == null) {
            version = firstNonblankLine(result.stderr());
        }
        if (version == null || version.isBlank()) {
            throw new CodexExecutionException(0, "Codex version output was blank.");
        }
        return version.length() <= MAXIMUM_VERSION_CHARACTERS
                ? version
                : version.substring(0, MAXIMUM_VERSION_CHARACTERS);
    }

    private static String firstNonblankLine(String output) {
        String normalized = output.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            String withoutControls = line.chars()
                    .filter(character -> !Character.isISOControl(character))
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                    .toString()
                    .strip();
            if (!withoutControls.isBlank()) {
                return withoutControls;
            }
        }
        return null;
    }

    private static CodexExecutionException startFailure(RuntimeException exception) {
        return new CodexExecutionException(-1, "Codex version check could not start.", exception);
    }

    private static Path requireExistingDirectory(Path path) {
        Objects.requireNonNull(path, "Working directory");
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Working directory must exist and be a directory");
        }
        return path;
    }

    private static List<CodexExecutable> candidatesFor(String operatingSystem) {
        Objects.requireNonNull(operatingSystem, "Operating system");
        if (operatingSystem.toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of(
                    new CodexExecutable(List.of("codex")),
                    new CodexExecutable(List.of("codex.exe")),
                    new CodexExecutable(List.of("codex.cmd")));
        }
        return List.of(new CodexExecutable(List.of("codex")));
    }
}
