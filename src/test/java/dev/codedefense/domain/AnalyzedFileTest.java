package dev.codedefense.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AnalyzedFileTest {
    @Test
    void normalizesPortableRelativePathsAndRejectsUnsafePaths() {
        AnalyzedFile file = new AnalyzedFile("src\\main//App.java", 3, false, 42);

        assertEquals("src/main/App.java", file.path());
        assertThrows(IllegalArgumentException.class, () -> new AnalyzedFile("", 1, false, 1));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzedFile("/App.java", 1, false, 1));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzedFile("C:\\App.java", 1, false, 1));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzedFile("src/../App.java", 1, false, 1));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzedFile("./App.java", 1, false, 1));
        assertThrows(IllegalArgumentException.class, () -> new AnalyzedFile("App.java", 0, false, 1));
    }
}
