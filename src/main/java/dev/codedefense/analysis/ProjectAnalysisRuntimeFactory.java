package dev.codedefense.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.CodexCliAiProvider;
import dev.codedefense.ai.CodexCommandFactory;
import dev.codedefense.ai.CodexEnvironmentChecker;
import dev.codedefense.ai.CodexProcessEnvironment;
import dev.codedefense.ai.CodexProcessRunner;
import dev.codedefense.ai.CodexRuntimeConfig;
import dev.codedefense.ai.CodexTemporaryWorkspace;
import dev.codedefense.ai.JdkProcessExecutor;
import java.nio.file.Path;

/** Builds the production Iteration 5 graph without executing Codex eagerly. */
public final class ProjectAnalysisRuntimeFactory {
    public ProjectAnalyzer create() {
        CodexRuntimeConfig config = CodexRuntimeConfig.defaults();
        JdkProcessExecutor processExecutor = new JdkProcessExecutor();
        CodexProcessEnvironment processEnvironment = new CodexProcessEnvironment();
        ObjectMapper objectMapper = new ObjectMapper();
        var preflight = CodexEnvironmentChecker.forCurrentEnvironment(
                processExecutor, config, processEnvironment, Path.of(".").toAbsolutePath().normalize());
        var runner = new CodexProcessRunner(
                processExecutor,
                new CodexCommandFactory(),
                processEnvironment,
                config,
                objectMapper,
                CodexTemporaryWorkspace::create,
                System.getenv());
        var provider = new CodexCliAiProvider(preflight, runner);
        return new AiProjectAnalyzer(
                provider,
                new ProjectAnalysisPromptFactory(),
                new ProjectAnalysisSchemaLoader(),
                new ProjectAnalysisValidator(),
                objectMapper,
                config);
    }
}
