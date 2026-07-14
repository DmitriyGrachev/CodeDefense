package dev.codedefense.cli;

import dev.codedefense.domain.ScanSummary;
import dev.codedefense.scanner.FileSystemProjectScanner;
import dev.codedefense.scanner.InvalidProjectPathException;
import dev.codedefense.scanner.NoSupportedSourceFilesException;
import dev.codedefense.scanner.ScanPolicy;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "start", description = "Start a technical defense for a local repository.")
public final class StartCommand implements java.util.concurrent.Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", defaultValue = ".", paramLabel = "PATH", description = "Repository path (default: current directory).")
    private Path path;

    @Option(names = "--dry-run", description = "Discover supported files without starting a defense.")
    private boolean dryRun;

    public Path path() {
        return path;
    }

    @Override
    public Integer call() {
        if (!dryRun) {
            System.out.println("Start is a placeholder; the technical defense workflow begins in a later iteration.");
            return ExitCodes.SUCCESS;
        }

        try {
            ScanSummary summary = new FileSystemProjectScanner().scan(path, ScanPolicy.defaults());
            System.out.printf(
                    "Discovered files: %d%nIgnored files: %d%nAccepted candidates: %d%n",
                    summary.discoveredFileCount(),
                    summary.ignoredFileCount(),
                    summary.acceptedCandidateCount()
            );
            return ExitCodes.SUCCESS;
        } catch (InvalidProjectPathException exception) {
            System.err.println(exception.getMessage());
            return ExitCodes.INVALID_PROJECT_PATH;
        } catch (NoSupportedSourceFilesException exception) {
            System.err.println(exception.getMessage());
            return ExitCodes.NO_SUPPORTED_SOURCE_FILES;
        }
    }
}
