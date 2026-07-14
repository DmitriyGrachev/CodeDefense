package dev.codedefense.scanner;

import dev.codedefense.domain.ScanSummary;
import java.nio.file.Path;

public interface ProjectScanner {
    ScanSummary scan(Path root, ScanPolicy policy);
}
