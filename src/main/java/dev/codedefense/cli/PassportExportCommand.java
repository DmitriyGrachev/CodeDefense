package dev.codedefense.cli;

import dev.codedefense.application.ExportChangePassportUseCase;
import dev.codedefense.passport.ChangePassportPaths;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.FileSystemChangePassportStore;
import dev.codedefense.passport.MarkdownChangePassportRenderer;
import dev.codedefense.passport.PassportReceiptJsonCodec;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "export", mixinStandardHelpOptions = true,
        description = "Export the latest source-free Passport receipt.")
public final class PassportExportCommand implements Callable<Integer> {
    private final ExportChangePassportUseCase useCase;
    @Parameters(index = "0", defaultValue = ".", paramLabel = "PATH") private Path path;
    @Option(names = "--format", required = true, description = "Export format: json.") private String format;
    @Option(names = "--output", required = true, paramLabel = "FILE") private Path output;
    @Spec private CommandSpec commandSpec;
    public PassportExportCommand() {
        var codec = new PassportReceiptJsonCodec();
        this.useCase = new ExportChangePassportUseCase(
                new FileSystemChangePassportStore(ChangePassportPaths.defaults(),
                        new MarkdownChangePassportRenderer(), Clock.systemUTC()), codec);
    }
    PassportExportCommand(ExportChangePassportUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "useCase");
    }
    @Override public Integer call() {
        if (!"json".equalsIgnoreCase(format)) {
            commandSpec.commandLine().getErr().println("Only --format json is supported.");
            return ExitCodes.INVALID_USAGE;
        }
        try { return useCase.export(path, output, commandSpec.commandLine().getOut(), commandSpec.commandLine().getErr()); }
        catch (ChangePassportPersistenceException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }
}
