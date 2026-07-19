package dev.codedefense.sample;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleProjectExtractorTest {
    @TempDir
    Path temporaryRoot;

    @Test
    void extractsTheExactProjectUnderItsFixedRootAndCleansItUp() throws Exception {
        SampleProjectExtractor extractor = extractor(archive(requiredFiles()), SampleProjectConfig.defaults(),
                SampleProjectExtractor::deleteTree);
        Path temporaryWorkspace;

        try (SampleProjectExtractor.ExtractedSampleProject extracted = extractor.extract()) {
            assertEquals("codedefense-sample-news-service", extracted.projectRoot().getFileName().toString());
            assertEquals(extracted.projectRoot().toAbsolutePath().normalize(), extracted.projectRoot());
            assertTrue(Files.isRegularFile(extracted.projectRoot().resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS));
            assertEquals("# Sample\n", Files.readString(extracted.projectRoot().resolve("README.md")));
            temporaryWorkspace = extracted.projectRoot().getParent();
        }

        assertFalse(Files.exists(temporaryWorkspace, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void closeIsIdempotent() throws Exception {
        SampleProjectExtractor.ExtractedSampleProject extracted = extractor(archive(requiredFiles()),
                SampleProjectConfig.defaults(), SampleProjectExtractor::deleteTree).extract();

        extracted.close();
        extracted.close();
    }

    @Test
    void reportsUnavailableWhenTheEmbeddedResourceCannotBeOpened() {
        SampleProjectExtractor extractor = new SampleProjectExtractor(
                SampleProjectConfig.defaults(), resource -> null,
                prefix -> Files.createTempDirectory(temporaryRoot, prefix),
                SampleProjectExtractor::deleteTree);

        SampleProjectException exception = assertThrows(SampleProjectException.class, extractor::extract);

        assertEquals(SampleProjectException.UNAVAILABLE_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsTraversalBeforeWritingOutsideTheTemporaryWorkspace() {
        Map<String, String> files = new LinkedHashMap<>(requiredFiles());
        files.put("../outside.txt", "not allowed\n");
        SampleProjectExtractor extractor = extractor(archive(files), SampleProjectConfig.defaults(),
                SampleProjectExtractor::deleteTree);

        SampleProjectException exception = assertThrows(SampleProjectException.class, extractor::extract);

        assertEquals(SampleProjectException.PREPARATION_FAILURE_MESSAGE, exception.getMessage());
        assertFalse(Files.exists(temporaryRoot.resolve("outside.txt"), LinkOption.NOFOLLOW_LINKS));
        assertNoWorkspaceRemains();
    }

    @Test
    void rejectsAnArchiveMemberMarkedAsASymbolicLink() {
        byte[] archive = archive(requiredFiles());
        markFirstCentralDirectoryEntryAsUnixSymbolicLink(archive);
        SampleProjectExtractor extractor = extractor(archive, SampleProjectConfig.defaults(),
                SampleProjectExtractor::deleteTree);

        SampleProjectException exception = assertThrows(SampleProjectException.class, extractor::extract);

        assertEquals(SampleProjectException.PREPARATION_FAILURE_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsAnEntryThatExceedsItsBoundedExpandedSize() {
        Map<String, String> files = new LinkedHashMap<>(requiredFiles());
        files.put("README.md", "x".repeat(64));
        SampleProjectConfig config = new SampleProjectConfig("sample/sample-project.zip", 1024, 32, 32, 1024, 240);
        SampleProjectExtractor extractor = extractor(archive(files), config, SampleProjectExtractor::deleteTree);

        SampleProjectException exception = assertThrows(SampleProjectException.class, extractor::extract);

        assertEquals(SampleProjectException.PREPARATION_FAILURE_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsMalformedAndOversizedArchivesWithoutLeakingTheirDetails() {
        SampleProjectException malformed = assertThrows(SampleProjectException.class,
                () -> extractor("private malformed archive".getBytes(StandardCharsets.UTF_8),
                        SampleProjectConfig.defaults(), SampleProjectExtractor::deleteTree).extract());
        SampleProjectConfig tinyArchive = new SampleProjectConfig("sample/sample-project.zip", 32, 32, 32, 32, 240);
        SampleProjectException oversized = assertThrows(SampleProjectException.class,
                () -> extractor(archive(requiredFiles()), tinyArchive, SampleProjectExtractor::deleteTree).extract());

        assertEquals(SampleProjectException.PREPARATION_FAILURE_MESSAGE, malformed.getMessage());
        assertEquals(SampleProjectException.PREPARATION_FAILURE_MESSAGE, oversized.getMessage());
        assertFalse(malformed.getMessage().contains("private"));
        assertNoWorkspaceRemains();
    }

    @Test
    void rejectsEntryCountAndExpandedOutputBounds() {
        Map<String, String> tooMany = new LinkedHashMap<>(requiredFiles());
        tooMany.put("unexpected.txt", "extra\n");
        SampleProjectConfig countConfig = new SampleProjectConfig("sample/sample-project.zip", 4096, 15, 64, 960, 240);
        SampleProjectConfig expandedConfig = new SampleProjectConfig("sample/sample-project.zip", 4096, 32, 64, 100, 240);

        assertRejected(archive(tooMany), countConfig);
        assertRejected(archive(requiredFiles()), expandedConfig);
    }

    @Test
    void rejectsAbsoluteDriveDotEmptyAndBackslashEntryNames() {
        for (String unsafePath : List.of("/outside.txt", "C:outside.txt", "src/./unsafe.txt",
                "src//unsafe.txt", "src\\unsafe.txt")) {
            Map<String, String> files = new LinkedHashMap<>(requiredFiles());
            files.put(unsafePath, "unsafe\n");
            assertRejected(archive(files), SampleProjectConfig.defaults());
        }
    }

    @Test
    void rejectsDuplicateEntriesDirectoryCollisionsMissingFilesAndUnexpectedFiles() {
        List<ArchiveItem> duplicateItems = new ArrayList<>();
        requiredFiles().forEach((path, content) -> duplicateItems.add(new ArchiveItem(path, content)));
        duplicateItems.add(new ArchiveItem("README.md", "second value\n"));
        assertRejected(archiveAllowingDuplicateNames(duplicateItems), SampleProjectConfig.defaults());

        List<ArchiveItem> directoryItems = new ArrayList<>();
        requiredFiles().forEach((path, content) -> directoryItems.add(new ArchiveItem(path, content)));
        directoryItems.add(new ArchiveItem("src/", ""));
        assertRejected(archiveAllowingDuplicateNames(directoryItems), SampleProjectConfig.defaults());

        Map<String, String> missing = new LinkedHashMap<>(requiredFiles());
        missing.remove("pom.xml");
        assertRejected(archive(missing), SampleProjectConfig.defaults());

        Map<String, String> unexpected = new LinkedHashMap<>(requiredFiles());
        unexpected.put("generated/output.txt", "extra\n");
        assertRejected(archive(unexpected), SampleProjectConfig.defaults());
    }

    @Test
    void rejectsEmptyRequiredFilesAndLeavesNoPartialWorkspace() {
        Map<String, String> files = new LinkedHashMap<>(requiredFiles());
        files.put("README.md", "");

        assertRejected(archive(files), SampleProjectConfig.defaults());
        assertNoWorkspaceRemains();
    }

    @Test
    void reportsAStableCleanupFailure() throws Exception {
        SampleProjectExtractor extractor = extractor(archive(requiredFiles()), SampleProjectConfig.defaults(),
                path -> { throw new IOException("private temporary path"); });
        SampleProjectExtractor.ExtractedSampleProject extracted = extractor.extract();

        SampleProjectException exception = assertThrows(SampleProjectException.class, extracted::close);

        assertEquals(SampleProjectException.CLEANUP_FAILURE_MESSAGE, exception.getMessage());
        SampleProjectExtractor.deleteTree(extracted.projectRoot().getParent());
    }

    private SampleProjectExtractor extractor(byte[] archive, SampleProjectConfig config,
            SampleProjectExtractor.DeletionStrategy deletionStrategy) {
        return new SampleProjectExtractor(config,
                resource -> new ByteArrayInputStream(archive),
                prefix -> Files.createTempDirectory(temporaryRoot, prefix),
                deletionStrategy);
    }

    private void assertRejected(byte[] archive, SampleProjectConfig config) {
        SampleProjectException exception = assertThrows(SampleProjectException.class,
                () -> extractor(archive, config, SampleProjectExtractor::deleteTree).extract());
        assertEquals(SampleProjectException.PREPARATION_FAILURE_MESSAGE, exception.getMessage());
    }

    private void assertNoWorkspaceRemains() {
        try (var children = Files.list(temporaryRoot)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith("codedefense-sample-")));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] archive(Map<String, String> files) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> file : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(file.getKey()));
                    zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void markFirstCentralDirectoryEntryAsUnixSymbolicLink(byte[] archive) {
        for (int index = 0; index <= archive.length - 46; index++) {
            if ((archive[index] & 0xff) == 0x50 && (archive[index + 1] & 0xff) == 0x4b
                    && (archive[index + 2] & 0xff) == 0x01 && (archive[index + 3] & 0xff) == 0x02) {
                archive[index + 5] = 3;
                archive[index + 38] = 0;
                archive[index + 39] = 0;
                archive[index + 40] = 0;
                archive[index + 41] = (byte) 0xa0;
                return;
            }
        }
        throw new AssertionError("ZIP central directory entry not found");
    }

    private static Map<String, String> requiredFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : requiredPaths()) {
            files.put(path, path.equals("README.md") ? "# Sample\n" : "package sample;\n");
        }
        return files;
    }

    private static byte[] archiveAllowingDuplicateNames(List<ArchiveItem> items) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            List<Integer> localOffsets = new ArrayList<>();
            for (ArchiveItem item : items) {
                localOffsets.add(output.size());
                byte[] name = item.path().getBytes(StandardCharsets.UTF_8);
                byte[] content = item.content().getBytes(StandardCharsets.UTF_8);
                CRC32 checksum = new CRC32();
                checksum.update(content);
                writeInt(output, 0x04034b50);
                writeShort(output, 20);
                writeShort(output, 0x0800);
                writeShort(output, 0);
                writeShort(output, 0);
                writeShort(output, 0);
                writeInt(output, checksum.getValue());
                writeInt(output, content.length);
                writeInt(output, content.length);
                writeShort(output, name.length);
                writeShort(output, 0);
                output.write(name);
                output.write(content);
            }
            int centralOffset = output.size();
            for (int index = 0; index < items.size(); index++) {
                ArchiveItem item = items.get(index);
                byte[] name = item.path().getBytes(StandardCharsets.UTF_8);
                byte[] content = item.content().getBytes(StandardCharsets.UTF_8);
                CRC32 checksum = new CRC32();
                checksum.update(content);
                writeInt(output, 0x02014b50);
                writeShort(output, 20);
                writeShort(output, 20);
                writeShort(output, 0x0800);
                writeShort(output, 0);
                writeShort(output, 0);
                writeShort(output, 0);
                writeInt(output, checksum.getValue());
                writeInt(output, content.length);
                writeInt(output, content.length);
                writeShort(output, name.length);
                writeShort(output, 0);
                writeShort(output, 0);
                writeShort(output, 0);
                writeShort(output, 0);
                writeInt(output, 0);
                writeInt(output, localOffsets.get(index));
                output.write(name);
            }
            int centralSize = output.size() - centralOffset;
            writeInt(output, 0x06054b50);
            writeShort(output, 0);
            writeShort(output, 0);
            writeShort(output, items.size());
            writeShort(output, items.size());
            writeInt(output, centralSize);
            writeInt(output, centralOffset);
            writeShort(output, 0);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream output, long value) {
        output.write((int) (value & 0xff));
        output.write((int) ((value >>> 8) & 0xff));
        output.write((int) ((value >>> 16) & 0xff));
        output.write((int) ((value >>> 24) & 0xff));
    }

    private record ArchiveItem(String path, String content) {
    }

    private static List<String> requiredPaths() {
        return List.of(
                "README.md",
                "pom.xml",
                "src/main/java/com/codedefense/sample/news/Article.java",
                "src/main/java/com/codedefense/sample/news/ArticleApplication.java",
                "src/main/java/com/codedefense/sample/news/ArticleController.java",
                "src/main/java/com/codedefense/sample/news/ArticleCreatedEvent.java",
                "src/main/java/com/codedefense/sample/news/ArticleRepository.java",
                "src/main/java/com/codedefense/sample/news/ArticleScheduler.java",
                "src/main/java/com/codedefense/sample/news/ArticleService.java",
                "src/main/java/com/codedefense/sample/news/FeedArticle.java",
                "src/main/java/com/codedefense/sample/news/FeedClient.java",
                "src/main/java/com/codedefense/sample/news/NotificationPublisher.java",
                "src/main/java/com/codedefense/sample/news/OpenAiSummaryService.java",
                "src/main/java/com/codedefense/sample/news/RetryingArticleProcessor.java",
                "src/main/resources/application.yml");
    }
}
