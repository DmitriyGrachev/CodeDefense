package dev.codedefense.jetbrains.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import dev.codedefense.jetbrains.process.BridgeLineCodec;
import dev.codedefense.jetbrains.process.BridgeProcess;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher;
import dev.codedefense.jetbrains.status.PassportStatusService;
import dev.codedefense.jetbrains.settings.CodeDefenseSettings;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class CodeDefenseToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        String basePath = project.getBasePath();
        if (basePath == null) return;
        Path bundledJar = bundledCliPath();
        if (bundledJar == null) return;
        CodeDefenseSettings settings = CodeDefenseSettings.getInstance();
        Path cliJar = settings.resolveCliJar(bundledJar);
        CodeDefenseLauncher launcher = CodeDefenseLauncher.production(cliJar);
        PassportStatusService statusService = PassportStatusService.production(cliJar);
        Path projectRoot = Path.of(basePath);
        var view = new SwingCodeDefenseToolWindowView();
        var configured = settings.getState();
        view.configureDefaults(CodeDefenseLauncher.Selector.valueOf(configured.defaultSelector),
                configured.defaultFocus);
        var codec = new BridgeLineCodec();
        var controller = new CodeDefenseToolWindowController(view,
                (spec, events) -> adapt(launcher.launch(Path.of(basePath), spec, events)), codec,
                action -> ApplicationManager.getApplication().invokeLater(action),
                command -> Thread.ofVirtual().name("codedefense-plugin-launch").start(command),
                () -> statusService.refresh(projectRoot),
                path -> openPassport(project, path));
        view.bind(controller);
        Disposer.register(project, controller);
        ApplicationManager.getApplication().getMessageBus().connect(controller)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override public void after(java.util.List<? extends com.intellij.openapi.vfs.newvfs.events.VFileEvent> events) {
                        String root = projectRoot.toAbsolutePath().normalize().toString();
                        if (events.stream().map(com.intellij.openapi.vfs.newvfs.events.VFileEvent::getPath)
                                .anyMatch(path -> path.startsWith(root)
                                        && path.replace('\\', '/').contains("/.git/"))) {
                            controller.scheduleRefresh();
                        }
                    }
                });
        toolWindow.getContentManager().addContent(
                ContentFactory.getInstance().createContent(view.component(), "", false));
        controller.refresh();
    }

    private static Path bundledCliPath() {
        try {
            var codeSource = CodeDefenseToolWindowFactory.class.getProtectionDomain().getCodeSource();
            return codeSource == null ? null
                    : bundledCliPath(Path.of(codeSource.getLocation().toURI()));
        } catch (Exception exception) {
            return null;
        }
    }

    static Path bundledCliPath(Path codeLocation) {
        Path normalized = codeLocation.toAbsolutePath().normalize();
        Path libDirectory = normalized.getParent();
        if (libDirectory == null || libDirectory.getFileName() == null
                || !libDirectory.getFileName().toString().equalsIgnoreCase("lib")) {
            return null;
        }
        Path pluginRoot = libDirectory.getParent();
        return pluginRoot == null ? null
                : pluginRoot.resolve("cli").resolve("codedefense.jar");
    }

    private CodeDefenseToolWindowController.Session adapt(BridgeProcess process) {
        return new CodeDefenseToolWindowController.Session() {
            @Override public void send(byte[] request) { process.send(request); }
            @Override public void cancel() { process.cancel(); }
            @Override public java.util.concurrent.CompletableFuture<Integer> completion() {
                return process.completion();
            }
        };
    }

    private void openPassport(Project project, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Passport path is invalid.");
        }
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(normalized);
        if (file == null || file.isDirectory()) throw new IllegalArgumentException("Passport path is invalid.");
        ApplicationManager.getApplication().invokeLater(() ->
                FileEditorManager.getInstance(project).openFile(file, true));
    }
}
