package dev.codedefense.cli;

import dev.codedefense.domain.ScanSummary;
import dev.codedefense.scanner.FileSystemProjectScanner;
import dev.codedefense.scanner.InvalidProjectPathException;
import dev.codedefense.scanner.NoSupportedSourceFilesException;
import dev.codedefense.scanner.ProjectScanner;
import dev.codedefense.scanner.ScanPolicy;
import dev.codedefense.scanner.ProjectSnapshotBuilder;
import dev.codedefense.application.CodeDefenseConfig;
import dev.codedefense.terminal.ConfirmationPrompt;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "start", description = "Start a technical defense for a local repository.")
public final class StartCommand implements java.util.concurrent.Callable<Integer> {
    private final ProjectScanner scanner;
    private final ProjectSnapshotBuilder snapshotBuilder;
    private final ConfirmationPrompt confirmation;

    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH", description = "Repository path (default: current directory).")
    private Path path;

    @Option(names = "--dry-run", description = "Discover supported files without starting a defense.")
    private boolean dryRun;
    @Option(names = {"-y", "--yes"}, description = "Skip confirmation.") private boolean yes;

    @Spec
    private CommandSpec commandSpec;

    public StartCommand() {
        this(new FileSystemProjectScanner(), new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), prompt -> {
            java.io.Console console = System.console();
            return console != null && "y".equalsIgnoreCase(console.readLine("%s ", prompt).trim());
        });
    }

    StartCommand(ProjectScanner scanner) {
        this(scanner, new ProjectSnapshotBuilder(CodeDefenseConfig.defaults()), prompt -> false);
    }
    StartCommand(ProjectScanner scanner, ProjectSnapshotBuilder snapshotBuilder, ConfirmationPrompt confirmation) {
        this.scanner = scanner; this.snapshotBuilder = snapshotBuilder; this.confirmation = confirmation;
    }

    public Path path() {
        return path;
    }

    @Override
    public Integer call() {
        try {
            ScanSummary summary = scanner.scan(path, ScanPolicy.defaults());
            var snapshot = snapshotBuilder.build(summary);
            commandSpec.commandLine().getOut().printf(
                    "Discovered files: %d%nIgnored files: %d%nAccepted candidates: %d%nSelected files: %d%nSnapshot bytes: %d%n",
                    summary.discoveredFileCount(),
                    summary.ignoredFileCount(),
                    summary.acceptedCandidateCount(), snapshot.selectedFiles().size(), snapshot.promptBytes()
            );
            snapshot.selectedFiles().forEach(file -> commandSpec.commandLine().getOut().println(file.relativePath() + " (" + file.renderedBytes() + " bytes)"));
            if (dryRun) return ExitCodes.SUCCESS;
            String prompt = "Send selected snapshot to Codex? [y/N]";
            if (!yes && System.console() == null) commandSpec.commandLine().getOut().println(prompt);
            if (!yes && !confirmation.confirm(prompt)) return ExitCodes.SUCCESS;
            commandSpec.commandLine().getOut().println("Codex execution arrives in Iteration 4.");
            return ExitCodes.SUCCESS;
        } catch (InvalidProjectPathException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.INVALID_PROJECT_PATH;
        } catch (NoSupportedSourceFilesException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        }
    }
}
