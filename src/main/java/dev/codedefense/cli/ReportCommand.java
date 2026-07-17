package dev.codedefense.cli;

import dev.codedefense.application.ShowLatestReportUseCase;
import dev.codedefense.report.CodeDefensePaths;
import dev.codedefense.report.FileSystemReportStore;
import dev.codedefense.report.MarkdownReportRenderer;
import dev.codedefense.report.ReportConfig;
import dev.codedefense.report.ReportPersistenceException;
import java.time.Clock;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "report", mixinStandardHelpOptions = true, description = "Show the latest CodeDefense report.")
public final class ReportCommand implements java.util.concurrent.Callable<Integer> {
    private final ShowLatestReportUseCase showLatestReport;

    @Spec
    private CommandSpec commandSpec;

    public ReportCommand() {
        this(new ShowLatestReportUseCase(new FileSystemReportStore(
                CodeDefensePaths.defaults(), ReportConfig.defaults(), new MarkdownReportRenderer(), Clock.systemUTC())));
    }

    public ReportCommand(ShowLatestReportUseCase showLatestReport) {
        this.showLatestReport = Objects.requireNonNull(showLatestReport, "Show latest report use case");
    }

    @Override
    public Integer call() {
        try {
            String report = showLatestReport.showLatest().orElse("No completed CodeDefense report is available yet.");
            commandSpec.commandLine().getOut().print(report.stripTrailing() + "\n");
            commandSpec.commandLine().getOut().flush();
            return ExitCodes.SUCCESS;
        } catch (ReportPersistenceException exception) {
            commandSpec.commandLine().getErr().println(exception.getMessage());
            return ExitCodes.REPORT_PERSISTENCE_FAILED;
        }
    }
}
