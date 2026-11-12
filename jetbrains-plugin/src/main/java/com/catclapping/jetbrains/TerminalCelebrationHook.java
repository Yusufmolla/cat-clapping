package com.catclapping.jetbrains;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.terminal.frontend.toolwindow.TerminalTabsManagerListener;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab;
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager;
import com.intellij.terminal.frontend.view.TerminalView;
import com.intellij.terminal.ui.TerminalWidget;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.future.FutureKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession;
import org.jetbrains.plugins.terminal.block.session.CommandFinishedEvent;
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener;
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandBlock;
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandExecutionListener;
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandFinishedEvent;
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalCommandStartedEvent;
import org.jetbrains.plugins.terminal.view.shellIntegration.TerminalShellIntegration;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Hooks JetBrains Terminal shell-integration events (Reworked Terminal & Block Terminal).
 */
public final class TerminalCelebrationHook implements StartupActivity.DumbAware {

    private static final Logger LOG = Logger.getInstance(TerminalCelebrationHook.class);

    private final Set<Object> attachedViews = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void runActivity(@NotNull Project project) {
        CelebrationService celebrationService = CelebrationService.getInstance(project);
        hookReworkedTerminal(project, celebrationService);
        hookBlockTerminal(project, celebrationService);
    }

    private void hookReworkedTerminal(Project project, CelebrationService celebrationService) {
        try {
            TerminalToolWindowTabsManager tabsManager = TerminalToolWindowTabsManager.getInstance(project);
            for (TerminalToolWindowTab tab : tabsManager.getTabs()) {
                attachToTerminalView(tab.getView(), celebrationService);
            }
            tabsManager.addListener(celebrationService, new TerminalTabsManagerListener() {
                @Override
                public void tabAdded(@NotNull TerminalToolWindowTab tab) {
                    attachToTerminalView(tab.getView(), celebrationService);
                }
            });
        } catch (LinkageError | RuntimeException e) {
            LOG.debug("Reworked terminal hook not available", e);
        }
    }

    private void attachToTerminalView(TerminalView view, CelebrationService celebrationService) {
        if (view == null || !attachedViews.add(view)) {
            return;
        }
        try {
            Deferred<TerminalShellIntegration> deferred = view.getShellIntegrationDeferred();
            CompletableFuture<TerminalShellIntegration> future = FutureKt.asCompletableFuture(deferred);
            future.thenAccept(integration -> {
                if (integration == null || celebrationService.isDisposed()) {
                    return;
                }
                Disposable listenerDisposable = Disposer.newDisposable(celebrationService, "CatClappingReworkedTerminal");
                integration.addCommandExecutionListener(listenerDisposable, new TerminalCommandExecutionListener() {
                    @Override
                    public void commandStarted(@NotNull TerminalCommandStartedEvent event) {
                        // No-op
                    }

                    @Override
                    public void commandFinished(@NotNull TerminalCommandFinishedEvent event) {
                        TerminalCommandBlock block = event.getCommandBlock();
                        Integer exitCode = block.getExitCode();
                        String command = block.getExecutedCommand();
                        if (exitCode != null && exitCode == 0 && command != null) {
                            onTerminalCommandSucceeded(command, celebrationService);
                        }
                    }
                });
            });
        } catch (LinkageError | RuntimeException e) {
            LOG.debug("Failed to attach shell integration listener to TerminalView", e);
        }
    }

    private void hookBlockTerminal(Project project, CelebrationService celebrationService) {
        try {
            TerminalToolWindowManager manager = TerminalToolWindowManager.getInstance(project);
            for (TerminalWidget widget : manager.getTerminalWidgets()) {
                attachToBlockWidget(widget, celebrationService);
            }
            manager.addNewTerminalSetupHandler(
                    widget -> attachToBlockWidget(widget, celebrationService),
                    celebrationService);
        } catch (LinkageError | RuntimeException e) {
            LOG.debug("Block terminal hook not available", e);
        }
    }

    private void attachToBlockWidget(TerminalWidget widget, CelebrationService celebrationService) {
        if (widget == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (celebrationService.isDisposed()) {
                return;
            }
            try {
                BlockTerminalSession session = DataManager.getInstance()
                        .getDataContext(widget.getComponent())
                        .getData(BlockTerminalSession.Companion.getDATA_KEY());
                if (session == null || !attachedViews.add(session)) {
                    return;
                }
                session.addCommandListener(new ShellCommandListener() {
                    @Override
                    public void commandFinished(@NotNull CommandFinishedEvent event) {
                        if (event.getExitCode() == 0) {
                            onTerminalCommandSucceeded(event.getCommand(), celebrationService);
                        }
                    }
                }, celebrationService);
            } catch (LinkageError | RuntimeException e) {
                LOG.debug("Failed to attach BlockTerminalSession listener", e);
            }
        });
    }

    private static void onTerminalCommandSucceeded(String command, CelebrationService celebrationService) {
        if (CommandMatcher.isCommandEnabled(command, CatClappingSettings.getInstance().getEnabledCommands())) {
            celebrationService.celebrate();
        }
    }
}
