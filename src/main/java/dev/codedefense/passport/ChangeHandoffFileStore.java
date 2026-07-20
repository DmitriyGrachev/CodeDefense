package dev.codedefense.passport;
import java.nio.file.Path;
public interface ChangeHandoffFileStore { Path write(Path output, byte[] content, boolean overwrite); byte[] read(Path input); }
