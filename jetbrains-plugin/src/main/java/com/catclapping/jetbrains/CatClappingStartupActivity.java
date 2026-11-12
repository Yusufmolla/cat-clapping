package com.catclapping.jetbrains;

import com.intellij.build.BuildViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.SuccessResult;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
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
        registerListeners(project);
        return Unit.INSTANCE;
    }

    private void registerListeners(@NotNull Project project) {
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
                        if (exitCode != 0) {
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
                                CatClappingSettings.getInstance().getEnabledCommands())) {
                            celebrationService.celebrate();
                        }
                    }
                });

        // 2. IDE Build tasks (e.g., CLion CMake/Make builds, Gradle/Maven builds).
        try {
            BuildViewManager buildViewManager = project.getService(BuildViewManager.class);
            if (buildViewManager != null) {
                buildViewManager.addListener((Object buildId, BuildEvent event) -> {
                    if (event instanceof FinishBuildEvent finishEvent
                            && finishEvent.getResult() instanceof SuccessResult
                            && CommandMatcher.isBuildEnabled(
                                    CatClappingSettings.getInstance().getEnabledCommands())) {
                        celebrationService.celebrate();
                    }
                }, celebrationService);
            }
        } catch (Throwable t) {
            LOG.debug("BuildViewManager listener unavailable", t);
        }
    }
}
