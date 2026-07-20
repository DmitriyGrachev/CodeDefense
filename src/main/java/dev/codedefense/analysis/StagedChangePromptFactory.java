package dev.codedefense.analysis;

import dev.codedefense.domain.ProjectSnapshot;
import dev.codedefense.domain.StagedChange;
import dev.codedefense.domain.DefenseFocus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class StagedChangePromptFactory {
    private static final String RESOURCE_PATH = "prompts/analyze-staged-change.md";
    private static final int MAXIMUM_TEMPLATE_BYTES = 64 * 1024;
    private final ClassLoader classLoader;

    public StagedChangePromptFactory() { this(StagedChangePromptFactory.class.getClassLoader()); }
    StagedChangePromptFactory(ClassLoader classLoader) { this.classLoader = Objects.requireNonNull(classLoader); }

    public String create(StagedChange change, ProjectSnapshot snapshot) {
        return create(change, snapshot, DefenseFocus.BALANCED);
    }

    public String create(StagedChange change, ProjectSnapshot snapshot, DefenseFocus focus) {
        Objects.requireNonNull(change, "Staged change");
        Objects.requireNonNull(snapshot, "Project snapshot");
        String payload = "Project root: " + change.repositoryRoot() + "\n"
                + "Project name: " + snapshot.projectName() + "\n"
                + "Project type: " + snapshot.projectType() + "\n"
                + "diff fingerprint: " + change.diffFingerprint() + "\n"
                + "Changed files: " + change.files().size() + "\n"
                + "Added lines: " + change.addedLines() + "\n"
                + "Deleted lines: " + change.deletedLines() + "\n\n"
                + snapshot.promptContent();
        String boundary = boundaryFor(payload);
        String directive = DefenseFocusPolicy.forFocus(Objects.requireNonNull(focus, "focus")).analysisInstruction();
        return loadInstructions() + "\n\n" + directive + "\n\nBEGIN " + boundary + "\n" + payload + "\nEND " + boundary + "\n";
    }

    private static String boundaryFor(String content) {
        String boundary = "CODEDEFENSE_UNTRUSTED_STAGED_CHANGE";
        while (content.contains(boundary)) { boundary += "_X"; }
        return boundary;
    }

    private String loadInstructions() {
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) throw unavailable();
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAXIMUM_TEMPLATE_BYTES) throw unavailable();
                output.write(buffer, 0, read);
            }
            return decode(output.toByteArray()).replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException | RuntimeException exception) { throw unavailable(); }
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("Staged change prompt resource is unavailable."); }
}
