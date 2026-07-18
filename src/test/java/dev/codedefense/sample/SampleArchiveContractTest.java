package dev.codedefense.sample;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleArchiveContractTest {
    @Test
    void embeddedArchiveHasExactlyTheApprovedTextOnlyProjectFiles() throws Exception {
        Map<String, String> contents = contents();

        assertEquals(expectedPathsInSortedOrder(), List.copyOf(contents.keySet()));
        assertTrue(contents.values().stream().noneMatch(value -> value.indexOf('\0') >= 0));
        assertTrue(contents.values().stream().noneMatch(String::isBlank));
        assertTrue(contents.values().stream().noneMatch(value -> value.indexOf('\r') >= 0));
        assertTrue(contents.keySet().stream().noneMatch(path -> path.endsWith(".env") || path.contains("/target/")
                || path.contains("/generated/") || path.endsWith(".class") || path.endsWith(".jar")));
        assertTrue(contents.keySet().stream().allMatch(path -> path.endsWith(".java") || path.endsWith(".md")
                || path.endsWith(".xml") || path.endsWith(".yml")));
    }

    @Test
    void embeddedProjectContainsTheScheduledFeedAndPersistenceWorkflow() throws Exception {
        Map<String, String> contents = contents();

        assertTrue(contents.get("pom.xml").contains("codedefense-sample-news-service"));
        assertTrue(contents.get("pom.xml").contains("spring-boot-starter"));
        String application = contents.get("src/main/java/com/codedefense/sample/news/ArticleApplication.java");
        assertTrue(application
                .contains("package com.codedefense.sample.news;"));
        assertTrue(application.contains("import org.springframework.retry.annotation.EnableRetry;"));
        assertTrue(application.contains("@EnableRetry"));
        assertTrue(contents.get("pom.xml").contains("spring-retry"));
        assertTrue(contents.get("pom.xml").contains("spring-boot-starter-aop"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleScheduler.java").contains("@Scheduled"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleScheduler.java").contains("public synchronized void pollFeed()"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleScheduler.java").contains("feedClient"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleScheduler.java").contains("retryingArticleProcessor"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/RetryingArticleProcessor.java").contains("@Retryable"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/RetryingArticleProcessor.java").contains("articleService.createArticle"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleService.java").contains("UUID.randomUUID()"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleService.java").contains("articleRepository.save"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleService.java").contains("notificationPublisher.publish"));
        assertFalse(contents.get("src/main/java/com/codedefense/sample/news/Article.java").contains("unique = true"));
        assertTrue(contents.get("src/main/java/com/codedefense/sample/news/ArticleService.java")
                .indexOf("articleRepository.save")
                < contents.get("src/main/java/com/codedefense/sample/news/ArticleService.java")
                .indexOf("notificationPublisher.publish"));
        assertFalse(contents.values().stream().anyMatch(value -> value.toLowerCase().contains("outbox")));
    }

    @Test
    void readmeDescribesTheSampleWithoutGivingAwayInterviewTopics() throws Exception {
        String readme = contents().get("README.md").toLowerCase();

        for (String spoiler : List.of("intentional bug", "deliberately broken", "interview issue",
                "expected answer", "outbox missing", "duplicate vulnerability")) {
            assertFalse(readme.contains(spoiler));
        }
    }

    @Test
    void embeddedArchiveContainsNoCredentialOrPrivateKeyLiteral() throws Exception {
        String allText = String.join("\n", contents().values()).toLowerCase();

        for (String forbidden : List.of("api_key", "apikey", "token=", "password=", "begin private key")) {
            assertFalse(allText.contains(forbidden));
        }
    }

    private static Map<String, String> contents() throws Exception {
        InputStream resource = SampleArchiveContractTest.class.getClassLoader()
                .getResourceAsStream(SampleProjectConfig.DEFAULT_RESOURCE_PATH);
        assertNotNull(resource, "The embedded sample archive must be packaged as a main resource");
        Map<String, String> contents = new LinkedHashMap<>();
        try (resource; ZipInputStream zip = new ZipInputStream(resource, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertFalse(entry.isDirectory());
                contents.put(entry.getName(), readUtf8Text(zip));
            }
        }
        return contents;
    }

    private static String readUtf8Text(ZipInputStream zip) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            total += read;
            assertTrue(total <= 128 * 1024, "Sample archive entry must remain bounded");
            bytes.write(buffer, 0, read);
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString();
    }

    private static List<String> expectedPathsInSortedOrder() {
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
