package com.catclapping.jetbrains;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Persistent application settings for Cat Clapping.
 */
@Service(Service.Level.APP)
@State(name = "CatClappingSettings", storages = @Storage("cat-clapping.xml"))
public final class CatClappingSettings implements PersistentStateComponent<CatClappingSettings.State> {

    public static final class State {
        public List<String> enabledCommands = new ArrayList<>(CommandMatcher.supportedCommandIds());
        public boolean soundEnabled = true;
        public int volumePercent = DEFAULT_VOLUME_PERCENT;
        public int catSizePx = DEFAULT_CAT_SIZE_PX;
        public CatPosition catPosition = CatPosition.TOP_CENTER;
        public CatAnchor catAnchor = CatAnchor.EDITOR;
    }

    public static final int DEFAULT_VOLUME_PERCENT = 80;
    public static final int DEFAULT_CAT_SIZE_PX = 520;
    public static final int MIN_CAT_SIZE_PX = 80;
    public static final int MAX_CAT_SIZE_PX = 1000;

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
        if (this.state.catPosition == null) {
            this.state.catPosition = CatPosition.TOP_CENTER;
        }
        if (this.state.catAnchor == null) {
            this.state.catAnchor = CatAnchor.EDITOR;
        }
    }

    public List<String> getEnabledCommands() {
        return state.enabledCommands != null ? state.enabledCommands : CommandMatcher.supportedCommandIds();
    }

    public void setEnabledCommands(List<String> commands) {
        state.enabledCommands = new ArrayList<>(commands);
    }

    /**
     * Resolves stored command entries (including legacy {@code "*"}) into concrete command IDs
     * matching the settings UI selection model.
     */
    public static List<String> resolveCommandSelection(List<String> stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        Set<String> resolved = new LinkedHashSet<>();
        boolean hasWildcard = false;
        for (String item : stored) {
            if (item == null) {
                continue;
            }
            String normalized = item.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(normalized)) {
                hasWildcard = true;
            } else if (!normalized.isEmpty()) {
                resolved.add(normalized);
            }
        }
        if (hasWildcard) {
            Set<String> withSupported = new LinkedHashSet<>(CommandMatcher.supportedCommandIds());
            withSupported.addAll(resolved);
            return new ArrayList<>(withSupported);
        }
        return new ArrayList<>(resolved);
    }

    public boolean isSoundEnabled() {
        return state.soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        state.soundEnabled = soundEnabled;
    }

    public int getVolumePercent() {
        return clamp(state.volumePercent, 0, 100);
    }

    public void setVolumePercent(int volumePercent) {
        state.volumePercent = clamp(volumePercent, 0, 100);
    }

    public int getCatSizePx() {
        return clamp(state.catSizePx, MIN_CAT_SIZE_PX, MAX_CAT_SIZE_PX);
    }

    public void setCatSizePx(int catSizePx) {
        state.catSizePx = clamp(catSizePx, MIN_CAT_SIZE_PX, MAX_CAT_SIZE_PX);
    }

    public CatPosition getCatPosition() {
        return state.catPosition != null ? state.catPosition : CatPosition.TOP_CENTER;
    }

    public void setCatPosition(CatPosition catPosition) {
        state.catPosition = catPosition != null ? catPosition : CatPosition.TOP_CENTER;
    }

    public CatAnchor getCatAnchor() {
        return state.catAnchor != null ? state.catAnchor : CatAnchor.EDITOR;
    }

    public void setCatAnchor(CatAnchor catAnchor) {
        state.catAnchor = catAnchor != null ? catAnchor : CatAnchor.EDITOR;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
