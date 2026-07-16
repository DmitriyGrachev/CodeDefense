package dev.codedefense.analysis;

import dev.codedefense.domain.ProjectSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ProjectAnalysisPromptFactory {
    private static final String RESOURCE_PATH = "prompts/analyze-project.md";
    private static final String UNAVAILABLE_MESSAGE = "Project analysis prompt resource is unavailable.";
    private static final int MAXIMUM_TEMPLATE_BYTES = 64 * 1024;

    private final ClassLoader classLoader;

    public ProjectAnalysisPromptFactory() {
        this(ProjectAnalysisPromptFactory.class.getClassLoader());
    }

    ProjectAnalysisPromptFactory(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "Class loader");
    }

    public String create(ProjectSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Project snapshot");
        String untrustedPayload = new StringBuilder()
                .append("Project name: ").append(snapshot.projectName())
                .append("\nProject type: ").append(snapshot.projectType())
                .append("\nSelected files: ").append(snapshot.selectedFiles().size())
                .append("\nSnapshot bytes: ").append(snapshot.promptBytes())
                .append("\n\n")
                .append(snapshot.promptContent())
                .toString();
        String boundary = boundaryFor(untrustedPayload);
        return new StringBuilder(loadInstructions())
                .append("\n\nBEGIN ").append(boundary).append('\n')
                .append(untrustedPayload)
                .append("\nEND ").append(boundary).append('\n')
                .toString();
    }

    private static String boundaryFor(String content) {
        String boundary = "CODEDEFENSE_UNTRUSTED_SNAPSHOT";
        while (content.contains(boundary)) {
            boundary += "_X";
        }
        return boundary;
    }

    private String loadInstructions() {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw unavailable();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAXIMUM_TEMPLATE_BYTES) {
                    throw unavailable();
                }
                output.write(buffer, 0, read);
            }
            return decodeUtf8(output.toByteArray());
        } catch (IOException | RuntimeException exception) {
            throw unavailable();
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException(UNAVAILABLE_MESSAGE);
    }
}
