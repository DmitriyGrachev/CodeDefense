package dev.codedefense.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SampleProjectConfigTest {
    @Test
    void defaultsMatchTheApprovedEmbeddedArchiveBounds() {
        SampleProjectConfig config = SampleProjectConfig.defaults();

        assertEquals("sample/sample-project.zip", config.resourcePath());
        assertEquals(512 * 1024, config.maximumArchiveBytes());
        assertEquals(32, config.maximumEntries());
        assertEquals(128 * 1024, config.maximumEntryBytes());
        assertEquals(1024 * 1024, config.maximumExpandedBytes());
        assertEquals(240, config.maximumEntryPathCharacters());
    }

    @Test
    void requiresCapacityForEveryRequiredEmbeddedSampleFile() {
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("sample/sample-project.zip", 1, 14, 1, 1, 1));

        SampleProjectConfig config = new SampleProjectConfig("sample/sample-project.zip", 1, 15, 1, 1, 1);

        assertEquals(15, config.maximumEntries());
    }

    @Test
    void rejectsBlankResourcePathAndNonPositiveBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig(" ", 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("sample.zip", 0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("sample.zip", 1, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("sample.zip", 1, 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("sample.zip", 1, 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("sample.zip", 1, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("/sample.zip", 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("C:\\sample.zip", 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SampleProjectConfig("sample.zip", 1, 1, 4, 3, 1));
    }

    @Test
    void rejectsUnsafeClasspathResourcePathSegments() {
        for (String unsafePath : new String[] {
                "sample\\sample-project.zip",
                "sample//sample-project.zip",
                "./sample-project.zip",
                "sample/./sample-project.zip",
                "sample/../sample-project.zip",
                "sample/sample-project.zip/"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SampleProjectConfig(unsafePath, 1, 15, 1, 1, 1));
        }
    }
}
