package dev.codedefense.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.exception.CodexException;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes one schema-constrained Codex request inside an empty temporary workspace. */
public final class CodexProcessRunner {
    private static final int MAXIMUM_SCHEMA_BYTES = 256 * 1024;
    private static final int MAXIMUM_PROMPT_BYTES = 512 * 1024;
    private static final int BUFFER_SIZE = 8 * 1024;
    private final ProcessExecutor processExecutor;
    private final CodexCommandFactory commandFactory;
    private final CodexRuntimeConfig config;
    private final ObjectMapper objectMapper;
    private final CodexTemporaryWorkspace.Factory workspaceFactory;
    private final Map<String, String> environment;

    public CodexProcessRunner(
            ProcessExecutor processExecutor,
            CodexCommandFactory commandFactory,
            CodexProcessEnvironment processEnvironment,
            CodexRuntimeConfig config,
            ObjectMapper objectMapper,
            CodexTemporaryWorkspace.Factory workspaceFactory,
            Map<String, String> sourceEnvironment) {
        this.processExecutor = Objects.requireNonNull(processExecutor, "Process executor");
        this.commandFactory = Objects.requireNonNull(commandFactory, "Codex command factory");
        this.config = Objects.requireNonNull(config, "Codex runtime configuration");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper");
        this.workspaceFactory = Objects.requireNonNull(workspaceFactory, "Temporary workspace factory");
        this.environment = Objects.requireNonNull(processEnvironment, "Codex process environment")
                .sanitize(sourceEnvironment);
    }

    public StructuredCodexResult execute(CodexEnvironment codexEnvironment, StructuredCodexRequest request) {
        Objects.requireNonNull(codexEnvironment, "Codex environment");
        Objects.requireNonNull(request, "Structured Codex request");
        validateRequest(request);

        CodexTemporaryWorkspace workspace = createWorkspace();
        try (workspace) {
            writeSchema(workspace, request.schemaJson());
            List<String> command = commandFactory.create(
                    codexEnvironment.executable(),
                    request,
                    workspace.workspace(),
                    workspace.schemaPath(),
                    workspace.finalMessagePath());
            ProcessResult processResult = execute(command, workspace.workspace(), request);
            if (processResult.timedOut()) {
                throw new CodexTimeoutException();
            }
            if (processResult.exitCode() != 0) {
                throw new CodexExecutionException(
                        processResult.exitCode(), safeExecutionDiagnostic(processResult));
            }

            String finalJson = readFinalJson(workspace.finalMessagePath());
            requireJsonObject(finalJson, "Codex response was not a JSON object.");
            return new StructuredCodexResult(finalJson, processResult.duration(), request.model());
        } catch (CodexException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CodexInterruptedException(exception);
            }
            throw new CodexExecutionException(-1, "Codex execution could not complete.", exception);
        }
    }

    private ProcessResult execute(List<String> command, Path workspace, StructuredCodexRequest request) {
        ProcessSpec specification = new ProcessSpec(
                command,
                workspace,
                environment,
                request.prompt(),
                request.timeout(),
                config.terminationGracePeriod(),
                config.maximumCapturedStdoutBytes(),
                config.maximumCapturedStderrBytes());
        return processExecutor.execute(specification);
    }

    private void validateRequest(StructuredCodexRequest request) {
        if (utf8Bytes(request.schemaJson()) > MAXIMUM_SCHEMA_BYTES) {
            throw new InvalidCodexResponseException("Codex schema exceeds the maximum size.");
        }
        if (utf8Bytes(request.prompt()) > MAXIMUM_PROMPT_BYTES) {
            throw new InvalidCodexResponseException("Codex prompt exceeds the maximum size.");
        }
        requireJsonObject(request.schemaJson(), "Codex schema must be a JSON object.");
    }

    private void requireJsonObject(String json, String message) {
        try {
            JsonNode node = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(json);
            if (node == null || !node.isObject()) {
                throw new InvalidCodexResponseException(message);
            }
        } catch (IOException exception) {
            throw new InvalidCodexResponseException(message);
        }
    }

    private String readFinalJson(Path outputPath) {
        try {
            if (!Files.isRegularFile(outputPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new InvalidCodexResponseException("Codex did not produce a final response.");
            }
            byte[] bytes = readBounded(outputPath, config.maximumFinalResponseBytes());
            String finalJson = decodeUtf8(bytes);
            if (finalJson.isBlank()) {
                throw new InvalidCodexResponseException("Codex produced an empty final response.");
            }
            return finalJson;
        } catch (IOException exception) {
            throw new InvalidCodexResponseException("Codex final response could not be read.");
        }
    }

    private static byte[] readBounded(Path path, int limit) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, BUFFER_SIZE))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > limit - total) {
                    throw new InvalidCodexResponseException("Codex final response exceeds the maximum size.");
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toByteArray();
        }
    }

    private CodexTemporaryWorkspace createWorkspace() {
        try {
            return workspaceFactory.create();
        } catch (IOException exception) {
            throw new CodexExecutionException(-1, "Codex temporary workspace could not be created.");
        }
    }

    private static void writeSchema(CodexTemporaryWorkspace workspace, String schemaJson) {
        try {
            workspace.writeSchema(schemaJson);
        } catch (IOException exception) {
            throw new CodexExecutionException(-1, "Codex schema could not be prepared.");
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidCodexResponseException("Codex final response was not valid UTF-8.");
        }
    }

    private static String safeExecutionDiagnostic(ProcessResult result) {
        if (result.stderrTruncated()) {
            return "Codex CLI failed. Captured diagnostics were omitted and had been truncated.";
        }
        return "Codex CLI failed. Captured diagnostics were omitted to protect repository content.";
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

}
