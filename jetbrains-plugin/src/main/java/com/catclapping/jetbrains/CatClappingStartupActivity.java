package com.catclapping.jetbrains;

import com.intellij.build.BuildViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.SuccessResult;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers execution and build listeners when a project opens.
 */
public final class CatClappingStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(CatClappingStartupActivity.class);

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        // ProjectActivity runs on a background dispatcher; BuildViewManager/UI listener setup should run on EDT.
        ApplicationManager.getApplication().invokeLater(() -> registerListeners(project));
        return Unit.INSTANCE;
    }

    private void registerListeners(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        CelebrationService celebrationService = CelebrationService.getInstance(project);

        // 1. Run / Debug / Test configurations across PyCharm, CLion, IntelliJ, etc.
        project.getMessageBus().connect(celebrationService).subscribe(
                ExecutionManager.EXECUTION_TOPIC,
                new ExecutionListener() {
                    @Override
                    public void processTerminated(
                            @NotNull String executorId,
                            @NotNull ExecutionEnvironment env,
                            @NotNull ProcessHandler handler,
                            int exitCode) {
                        CatReaction reaction = exitCode == 0
                                ? CatReaction.CLAPPING
                                : (CommandMatcher.isErrorExitCode(exitCode) ? CatReaction.HUH : null);
                        if (reaction == null) {
                            return;
                        }
                        CatClappingSettings settings = CatClappingSettings.getInstance();
                        if (!settings.isEnabled(reaction)) {
                            return;
                        }
                        RunProfile profile = env.getRunProfile();
                        String profileName = profile.getName();
                        String configTypeId = env.getRunnerAndConfigurationSettings() != null
                                ? env.getRunnerAndConfigurationSettings().getType().getId()
                                : null;
                        boolean isDebug = DefaultDebugExecutor.EXECUTOR_ID.equalsIgnoreCase(executorId);
                        if (CommandMatcher.isRunConfigurationEnabled(
                                profileName,
                                configTypeId,
                                isDebug,
                                settings.getEnabledCommands(reaction))) {
                            celebrationService.trigger(reaction);
                        }
                    }
                });

        // 2. IDE Build tasks (e.g., CLion CMake/Make builds, Gradle/Maven builds).
        try {
            BuildViewManager buildViewManager = project.getService(BuildViewManager.class);
            if (buildViewManager != null) {
                buildViewManager.addListener((Object buildId, BuildEvent event) -> {
                    if (!(event instanceof FinishBuildEvent finishEvent)) {
                        return;
                    }
                    EventResult result = finishEvent.getResult();
                    CatReaction reaction;
                    if (result instanceof SuccessResult) {
                        reaction = CatReaction.CLAPPING;
                    } else if (result instanceof FailureResult) {
                        reaction = CatReaction.HUH;
                    } else {
                        return;
                    }
                    CatClappingSettings settings = CatClappingSettings.getInstance();
                    if (settings.isEnabled(reaction)
                            && CommandMatcher.isBuildEnabled(settings.getEnabledCommands(reaction))) {
                        celebrationService.trigger(reaction);
                    }
                }, celebrationService);
            }
        } catch (Throwable t) {
            LOG.debug("BuildViewManager listener unavailable", t);
        }
    }
}
