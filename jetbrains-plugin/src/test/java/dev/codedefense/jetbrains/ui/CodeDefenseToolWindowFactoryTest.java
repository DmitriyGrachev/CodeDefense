package dev.codedefense.jetbrains.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeDefenseToolWindowFactoryTest {
    @TempDir Path directory;

    @Test
    void queuedGateUpdateDoesNotMutateViewAfterWindowDisposal() {
        Disposable window = Disposer.newDisposable("disposed CodeDefense test window");
        int[] mutations = {0};
        Runnable queued = CodeDefenseToolWindowFactory.disposalAwareGateUpdate(
                window, () -> mutations[0]++);

        Disposer.dispose(window);
        queued.run();

        assertEquals(0, mutations[0]);
    }

    @Test
    void derivesBundledCliFromPluginClassResource() throws Exception {
        Path pluginRoot = directory.resolve("CodeDefense");
        Path pluginJar = pluginRoot.resolve("lib").resolve("codedefense-jetbrains-0.1.1.jar");
        var classResource = URI.create("jar:" + pluginJar.toUri()
                + "!/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.class").toURL();

        assertEquals(pluginRoot.resolve("cli").resolve("codedefense.jar"),
                CodeDefenseToolWindowFactory.bundledCliPath(classResource));
    }

    @Test
    void eventSignalsRefreshForProjectWindowGitIndexAndActivationButNotSourceFiles() {
        int[] refreshes = {0};
        int[] insightRefreshes = {0};
        var signals = new CodeDefenseToolWindowFactory.RefreshSignals(directory,
                () -> refreshes[0]++, () -> insightRefreshes[0]++);

        signals.projectOpened();
        signals.toolWindowShown("Other");
        signals.toolWindowShown("CodeDefense");
        assertEquals(1, insightRefreshes[0]);
        signals.gitRepositoryChanged(false);
        signals.gitRepositoryChanged(true);
        signals.vfsBatch(List.of(directory.resolve("src/Main.java").toString()));
        assertEquals(3, refreshes[0]);

        signals.vfsBatch(List.of(directory.resolve(".git/index").toString()));
        signals.applicationActivated(false);
        signals.applicationActivated(true);
        assertEquals(5, refreshes[0]);
        assertEquals(1, insightRefreshes[0]);
        signals.manualRefresh();
        assertEquals(6, refreshes[0]);
        assertEquals(2, insightRefreshes[0]);
        assertFalse(CodeDefenseToolWindowFactory.isIndexRelevant(directory,
                directory.resolve(".git/refs/heads/main").toString()));
        assertTrue(CodeDefenseToolWindowFactory.isIndexRelevant(directory,
                directory.resolve(".git/index.lock").toString()));
    }

    @Test
    void factoryUsesOnlyPublicGitRepositoryTopicAndProjectDisposableSubscriptions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/dev/codedefense/jetbrains/ui/CodeDefenseToolWindowFactory.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("GitRepository.GIT_REPO_CHANGE"));
        assertTrue(source.contains("GitRepositoryChangeListener"));
        assertTrue(source.contains("VirtualFileManager.VFS_CHANGES"));
        assertTrue(source.contains("ApplicationActivationListener.TOPIC"));
        assertTrue(source.contains("ToolWindowManagerListener.TOPIC"));
        assertTrue(source.contains("content.setDisposer(windowDisposable)"));
        assertTrue(source.contains("gateService.removeObserver(observer)"));
        assertFalse(source.contains("git4idea.repo.GitRepositoryImpl"));
        assertFalse(source.contains("LocalFileSystemImpl"));
    }
}
