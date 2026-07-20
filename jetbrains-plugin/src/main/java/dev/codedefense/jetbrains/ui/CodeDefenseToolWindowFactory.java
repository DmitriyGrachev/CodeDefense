package dev.codedefense.jetbrains.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.ContentFactory;
import dev.codedefense.jetbrains.gate.CodeDefenseProjectGateService;
import dev.codedefense.jetbrains.gate.StagedGateCoordinator;
import dev.codedefense.jetbrains.insights.RepositoryInsightsService;
import dev.codedefense.jetbrains.evidence.IntelliJEvidenceNavigator;
import dev.codedefense.jetbrains.process.BridgeLineCodec;
import dev.codedefense.jetbrains.process.BridgeProcess;
import dev.codedefense.jetbrains.process.CodeDefenseLauncher;
import dev.codedefense.jetbrains.status.PassportStatusService;
import dev.codedefense.jetbrains.settings.CodeDefenseSettings;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;

public final class CodeDefenseToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        String basePath = project.getBasePath();
        if (basePath == null) return;
        Path bundledJar = bundledCliPath(CodeDefenseToolWindowFactory.class
                .getResource("CodeDefenseToolWindowFactory.class"));
        if (bundledJar == null) return;
        CodeDefenseSettings settings = CodeDefenseSettings.getInstance();
        Path cliJar = settings.resolveCliJar(bundledJar);
        CodeDefenseLauncher launcher = CodeDefenseLauncher.production(cliJar);
        PassportStatusService statusService = PassportStatusService.production(cliJar);
        RepositoryInsightsService insightsService = RepositoryInsightsService.production(cliJar);
        CodeDefenseProjectGateService gateService = CodeDefenseProjectGateService.getInstance(project);
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
                path -> openPassport(project, path),
                gateService::requestRefresh,
                new IntelliJEvidenceNavigator(project, projectRoot),
                () -> insightsService.refresh(projectRoot));
        RefreshSignals refreshSignals = new RefreshSignals(projectRoot,
                gateService::requestRefresh, controller::refreshInsights);
        view.bind(controller);
        Disposable windowDisposable = Disposer.newDisposable("CodeDefense Tool Window");
        Disposer.register(project, windowDisposable);
        Disposer.register(windowDisposable, controller);
        StagedGateCoordinator.Observer observer = gate ->
                queueGateUpdate(windowDisposable, () -> view.showGateStatus(gate));
        gateService.addObserver(observer);
        Disposer.register(windowDisposable, () -> gateService.removeObserver(observer));
        gateService.cached().ifPresent(view::showGateStatus);

        project.getMessageBus().connect(windowDisposable)
                .subscribe(GitRepository.GIT_REPO_CHANGE, (GitRepositoryChangeListener) repository ->
                        refreshSignals.gitRepositoryChanged(repository.getProject() == project));
        project.getMessageBus().connect(windowDisposable)
                .subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
                    @Override public void toolWindowShown(ToolWindow shown) {
                        refreshSignals.toolWindowShown(shown.getId());
                    }
                });
        ApplicationManager.getApplication().getMessageBus().connect(windowDisposable)
                .subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
                    @Override public void applicationActivated(com.intellij.openapi.wm.IdeFrame frame) {
                        refreshSignals.applicationActivated(frame != null && frame.getProject() == project);
                    }
                });
        ApplicationManager.getApplication().getMessageBus().connect(windowDisposable)
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override public void after(java.util.List<? extends com.intellij.openapi.vfs.newvfs.events.VFileEvent> events) {
                        refreshSignals.vfsBatch(events.stream()
                                .map(com.intellij.openapi.vfs.newvfs.events.VFileEvent::getPath).toList());
                    }
                });
        var content = ContentFactory.getInstance().createContent(view.component(), "", false);
        content.setDisposer(windowDisposable);
        toolWindow.getContentManager().addContent(content);
        controller.initialize(toolWindow.isVisible());
    }

    static Path bundledCliPath(URL classResource) {
        return CodeDefenseProjectGateService.bundledCliPath(classResource);
    }

    static void queueGateUpdate(Disposable lifetime, Runnable update) {
        Runnable guardedUpdate = disposalAwareGateUpdate(lifetime, update);
        ApplicationManager.getApplication().invokeLater(guardedUpdate,
                ignored -> Disposer.isDisposed(lifetime));
    }

    static Runnable disposalAwareGateUpdate(Disposable lifetime, Runnable update) {
        Objects.requireNonNull(lifetime, "lifetime");
        Objects.requireNonNull(update, "update");
        return () -> {
            if (!Disposer.isDisposed(lifetime)) update.run();
        };
    }

    static boolean isIndexRelevant(Path projectRoot, String eventPath) {
        if (eventPath == null) return false;
        try {
            Path normalized = Path.of(eventPath).toAbsolutePath().normalize();
            Path index = projectRoot.toAbsolutePath().normalize().resolve(".git").resolve("index");
            return normalized.equals(index) || normalized.equals(index.resolveSibling("index.lock"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static final class RefreshSignals {
        private final Path projectRoot;
        private final Runnable gateRefresh;
        private final Runnable insightsRefresh;

        RefreshSignals(Path projectRoot, Runnable gateRefresh, Runnable insightsRefresh) {
            this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
            this.gateRefresh = Objects.requireNonNull(gateRefresh, "gateRefresh");
            this.insightsRefresh = Objects.requireNonNull(insightsRefresh, "insightsRefresh");
        }

        void projectOpened() { gateRefresh.run(); }
        void toolWindowShown(String id) {
            if ("CodeDefense".equals(id)) {
                gateRefresh.run();
                insightsRefresh.run();
            }
        }
        void gitRepositoryChanged(boolean thisProject) { if (thisProject) gateRefresh.run(); }
        void vfsBatch(List<String> paths) {
            if (paths.stream().anyMatch(path -> isIndexRelevant(projectRoot, path))) gateRefresh.run();
        }
        void applicationActivated(boolean thisProject) { if (thisProject) gateRefresh.run(); }
        void manualRefresh() {
            gateRefresh.run();
            insightsRefresh.run();
        }
    }

    private CodeDefenseToolWindowController.Session adapt(BridgeProcess process) {
        return new CodeDefenseToolWindowController.Session() {
            @Override public void confirm(boolean accepted) { process.sendConfirm(accepted); }
            @Override public void answer(String answer) { process.sendAnswer(answer); }
            @Override public void skip() { process.sendSkip(); }
            @Override public void provenanceConsent(String threadId, boolean consent) {
                process.sendProvenanceConsent(threadId, consent);
            }
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
