package dev.codedefense.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.codedefense.ai.exception.CodexException;
import dev.codedefense.ai.exception.CodexExecutionException;
import dev.codedefense.ai.exception.CodexInterruptedException;
import dev.codedefense.ai.exception.CodexTimeoutException;
import dev.codedefense.ai.exception.InvalidCodexResponseException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

        try (CodexTemporaryWorkspace workspace = workspaceFactory.create()) {
            workspace.writeSchema(request.schemaJson());
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
                throw new CodexExecutionException(processResult.exitCode(), boundedStderr(processResult));
            }

            String finalJson = readFinalJson(workspace.finalMessagePath());
            requireJsonObject(finalJson, "Codex response was not a JSON object.");
            return new StructuredCodexResult(finalJson, processResult.duration(), request.model());
        } catch (CodexException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new InvalidCodexResponseException("Codex temporary response could not be read.");
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
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new InvalidCodexResponseException(message);
            }
        } catch (IOException exception) {
            throw new InvalidCodexResponseException(message);
        }
    }

    private String readFinalJson(Path outputPath) throws IOException {
        if (!Files.isRegularFile(outputPath)) {
            throw new InvalidCodexResponseException("Codex did not produce a final response.");
        }
        byte[] bytes = readBounded(outputPath, config.maximumFinalResponseBytes());
        String finalJson = new String(bytes, StandardCharsets.UTF_8);
        if (finalJson.isBlank()) {
            throw new InvalidCodexResponseException("Codex produced an empty final response.");
        }
        return finalJson;
    }

    private static byte[] readBounded(Path path, int limit) throws IOException {
        try (InputStream input = Files.newInputStream(path);
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

    private String boundedStderr(ProcessResult result) {
        String normalized = result.stderr().replace("\r\n", "\n").replace('\r', '\n');
        String marker = "\n[stderr truncated]";
        boolean truncated = result.stderrTruncated() || utf8Bytes(normalized) > config.maximumCapturedStderrBytes();
        if (!truncated) {
            return normalized;
        }
        int contentLimit = Math.max(0, config.maximumCapturedStderrBytes() - utf8Bytes(marker));
        return utf8Prefix(normalized, contentLimit) + marker;
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String utf8Prefix(String value, int limit) {
        StringBuilder prefix = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = utf8Bytes(character);
            if (bytes + characterBytes > limit) {
                break;
            }
            prefix.append(character);
            bytes += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return prefix.toString();
    }
}
