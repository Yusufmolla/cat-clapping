package com.catclapping.jetbrains;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent application settings for Cat Clapping.
 */
@Service(Service.Level.APP)
@State(name = "CatClappingSettings", storages = @Storage("cat-clapping.xml"))
public final class CatClappingSettings implements PersistentStateComponent<CatClappingSettings.State> {

    public static final class State {
        public List<String> enabledCommands = new ArrayList<>(List.of("*"));
        public boolean soundEnabled = true;
    }

    private State state = new State();

    public static CatClappingSettings getInstance() {
        return ApplicationManager.getApplication().getService(CatClappingSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State loaded) {
        this.state = loaded;
        if (this.state.enabledCommands == null) {
            this.state.enabledCommands = new ArrayList<>();
        }
    }

    public List<String> getEnabledCommands() {
        return state.enabledCommands != null ? state.enabledCommands : List.of("*");
    }

    public void setEnabledCommands(List<String> commands) {
        state.enabledCommands = new ArrayList<>(commands);
    }

    public boolean isSoundEnabled() {
        return state.soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        state.soundEnabled = soundEnabled;
    }
}
