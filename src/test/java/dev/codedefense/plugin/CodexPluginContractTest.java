package dev.codedefense.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexPluginContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path PLUGIN = ROOT.resolve("plugins/codedefense");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void manifestDeclaresOnlyTheBundledSkillAndInstallMetadata() throws Exception {
        JsonNode manifest = json(PLUGIN.resolve(".codex-plugin/plugin.json"));

        assertEquals("codedefense", manifest.path("name").asText());
        assertEquals("0.1.0", manifest.path("version").asText());
        assertEquals("Show source-free staged Change Passport status and guide explicit CodeDefense workflows.",
                manifest.path("description").asText());
        assertEquals("./skills/", manifest.path("skills").asText());
        assertEquals("CodeDefense", manifest.path("interface").path("displayName").asText());
        assertEquals(List.of("Read"), JSON.convertValue(
                manifest.path("interface").path("capabilities"), List.class));
        assertFalse(manifest.has("apps"));
        assertFalse(manifest.has("mcpServers"));
        assertFalse(manifest.has("hooks"));
    }

    @Test
    void repositoryMarketplaceContainsOneAvailableLocalPlugin() throws Exception {
        JsonNode marketplace = json(ROOT.resolve(".agents/plugins/marketplace.json"));

        assertEquals("codedefense-local", marketplace.path("name").asText());
        assertEquals("CodeDefense Local", marketplace.path("interface").path("displayName").asText());
        assertEquals(1, marketplace.path("plugins").size());
        JsonNode plugin = marketplace.path("plugins").get(0);
        assertEquals("codedefense", plugin.path("name").asText());
        assertEquals("local", plugin.path("source").path("source").asText());
        assertEquals("./plugins/codedefense", plugin.path("source").path("path").asText());
        assertEquals("AVAILABLE", plugin.path("policy").path("installation").asText());
        assertEquals("ON_INSTALL", plugin.path("policy").path("authentication").asText());
        assertEquals("Productivity", plugin.path("category").asText());
    }

    @Test
    void pluginBundlesExactlyOneAdvisoryStopHook() throws Exception {
        Path hookFile = PLUGIN.resolve("hooks/hooks.json");
        assertTrue(Files.isRegularFile(hookFile));
        JsonNode hooks = json(hookFile);

        assertEquals(1, hooks.path("hooks").size());
        assertEquals(1, hooks.path("hooks").path("Stop").size());
        JsonNode group = hooks.path("hooks").path("Stop").get(0);
        assertFalse(group.has("matcher"));
        assertEquals(1, group.path("hooks").size());
        JsonNode handler = group.path("hooks").get(0);
        assertEquals("command", handler.path("type").asText());
        assertEquals(15, handler.path("timeout").asInt());
        assertTrue(handler.path("command").asText().contains("codedefense-hook.sh"));
        assertTrue(handler.path("commandWindows").asText().contains("codedefense-hook.ps1"));
        assertFalse(handler.has("async"));
    }

    @Test
    void skillHasMinimalFrontmatterAndSafeExplicitWorkflows() throws Exception {
        Path skillFile = PLUGIN.resolve("skills/codedefense/SKILL.md");
        assertTrue(Files.isRegularFile(skillFile));
        String skill = Files.readString(skillFile, StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n');
        String[] sections = skill.split("---", -1);

        assertTrue(skill.startsWith("---\n"));
        assertTrue(sections.length >= 3);
        Set<String> frontmatterKeys = Arrays.stream(sections[1].split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> line.substring(0, line.indexOf(':')))
                .collect(Collectors.toSet());
        assertEquals(Set.of("name", "description"), frontmatterKeys);
        assertTrue(skill.contains("codex-hook status"));
        assertTrue(skill.contains("prove --staged --dry-run ."));
        assertTrue(skill.contains("passport show ."));
        assertTrue(skill.contains("passport insights . --format json --limit 20"));
        for (String forbidden : List.of("--yes", "codex exec", "expectedKeyPoints")) {
            assertFalse(skill.contains(forbidden));
        }
        assertTrue(skill.contains("Never answer defense questions"));
        assertTrue(Files.isRegularFile(PLUGIN.resolve("cli/.gitkeep")));
    }

    @Test
    void packagingScriptsCreateOneIgnoredSelfContainedArchive() throws Exception {
        Path powerShellPath = ROOT.resolve("scripts/package-codex-plugin.ps1");
        Path posixPath = ROOT.resolve("scripts/package-codex-plugin.sh");
        assertTrue(Files.isRegularFile(powerShellPath));
        assertTrue(Files.isRegularFile(posixPath));
        String powerShell = Files.readString(powerShellPath, StandardCharsets.UTF_8);
        String posix = Files.readString(posixPath, StandardCharsets.UTF_8);

        for (String script : List.of(powerShell, posix)) {
            assertTrue(script.contains("target/codedefense.jar")
                    || script.contains("target\\codedefense.jar"));
            assertTrue(script.contains("codedefense-codex-plugin.zip"));
            assertTrue(script.contains("plugins/codedefense")
                    || script.contains("plugins\\codedefense"));
        }
        assertFalse(powerShell.contains("Invoke-Expression"));
        assertFalse(posix.contains("eval"));
        assertTrue(Files.readString(ROOT.resolve(".gitignore"), StandardCharsets.UTF_8)
                .contains("plugins/codedefense/cli/codedefense.jar"));
    }

    private static JsonNode json(Path path) throws IOException {
        return JSON.readTree(Files.readAllBytes(path));
    }
}
