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
import java.util.Objects;

/**
 * Persistent application settings for Cat Clapping and HUH Cat reactions.
 */
@Service(Service.Level.APP)
@State(name = "CatClappingSettings", storages = @Storage("cat-clapping.xml"))
public final class CatClappingSettings implements PersistentStateComponent<CatClappingSettings.State> {

    public static final int DEFAULT_VOLUME_PERCENT = 80;
    public static final int DEFAULT_CAT_SIZE_PX = CatReaction.CLAPPING.defaultSizePx();
    public static final int DEFAULT_HUH_CAT_SIZE_PX = CatReaction.HUH.defaultSizePx();
    public static final int MIN_CAT_SIZE_PX = 80;
    public static final int MAX_CAT_SIZE_PX = 1000;
    public static final double DEFAULT_CAT_POS_X = 0.5;
    public static final double DEFAULT_CAT_POS_Y = 0.0;

    public static final class State {
        // Clapping Cat (on success) — field names preserved from 0.1.8 for seamless upgrades.
        public boolean clappingEnabled = true;
        public List<String> enabledCommands = new ArrayList<>(CommandMatcher.supportedCommandIds());
        public boolean soundEnabled = true;
        public int volumePercent = DEFAULT_VOLUME_PERCENT;
        public int catSizePx = DEFAULT_CAT_SIZE_PX;
        public double catPosX = DEFAULT_CAT_POS_X;
        public double catPosY = DEFAULT_CAT_POS_Y;
        public CatAnchor catAnchor = CatAnchor.WINDOW;

        // HUH Cat (on error) — enabled by default.
        public boolean huhEnabled = true;
        public HuhClipMode huhClipMode = HuhClipMode.SECOND_HUH;
        public List<String> huhEnabledCommands = new ArrayList<>(CommandMatcher.supportedCommandIds());
        public boolean huhSoundEnabled = true;
        public int huhVolumePercent = DEFAULT_VOLUME_PERCENT;
        public int huhCatSizePx = DEFAULT_HUH_CAT_SIZE_PX;
        public double huhCatPosX = DEFAULT_CAT_POS_X;
        public double huhCatPosY = DEFAULT_CAT_POS_Y;
        public CatAnchor huhCatAnchor = CatAnchor.WINDOW;
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
        if (this.state.huhEnabledCommands == null) {
            this.state.huhEnabledCommands = new ArrayList<>();
        }
        this.state.catPosX = CatPlacement.clampNormalized(this.state.catPosX);
        this.state.catPosY = CatPlacement.clampNormalized(this.state.catPosY);
        this.state.huhCatPosX = CatPlacement.clampNormalized(this.state.huhCatPosX);
        this.state.huhCatPosY = CatPlacement.clampNormalized(this.state.huhCatPosY);
        if (this.state.catAnchor == null) {
            this.state.catAnchor = CatAnchor.WINDOW;
        }
        if (this.state.huhCatAnchor == null) {
            this.state.huhCatAnchor = CatAnchor.WINDOW;
        }
        if (this.state.huhClipMode == null) {
            this.state.huhClipMode = HuhClipMode.SECOND_HUH;
        }
    }

    public HuhClipMode getHuhClipMode() {
        return state.huhClipMode != null ? state.huhClipMode : HuhClipMode.SECOND_HUH;
    }

    public void setHuhClipMode(HuhClipMode mode) {
        state.huhClipMode = mode != null ? mode : HuhClipMode.SECOND_HUH;
    }

    public boolean isEnabled(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        return reaction == CatReaction.HUH ? state.huhEnabled : state.clappingEnabled;
    }

    public void setEnabled(CatReaction reaction, boolean enabled) {
        Objects.requireNonNull(reaction, "reaction");
        if (reaction == CatReaction.HUH) {
            state.huhEnabled = enabled;
        } else {
            state.clappingEnabled = enabled;
        }
    }

    public List<String> getEnabledCommands(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        List<String> list = reaction == CatReaction.HUH ? state.huhEnabledCommands : state.enabledCommands;
        return list != null ? list : CommandMatcher.supportedCommandIds();
    }

    public void setEnabledCommands(CatReaction reaction, List<String> commands) {
        Objects.requireNonNull(reaction, "reaction");
        List<String> copy = new ArrayList<>(commands);
        if (reaction == CatReaction.HUH) {
            state.huhEnabledCommands = copy;
        } else {
            state.enabledCommands = copy;
        }
    }

    public boolean isSoundEnabled(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        return reaction == CatReaction.HUH ? state.huhSoundEnabled : state.soundEnabled;
    }

    public void setSoundEnabled(CatReaction reaction, boolean soundEnabled) {
        Objects.requireNonNull(reaction, "reaction");
        if (reaction == CatReaction.HUH) {
            state.huhSoundEnabled = soundEnabled;
        } else {
            state.soundEnabled = soundEnabled;
        }
    }

    public int getVolumePercent(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        int raw = reaction == CatReaction.HUH ? state.huhVolumePercent : state.volumePercent;
        return clamp(raw, 0, 100);
    }

    public void setVolumePercent(CatReaction reaction, int volumePercent) {
        Objects.requireNonNull(reaction, "reaction");
        int clamped = clamp(volumePercent, 0, 100);
        if (reaction == CatReaction.HUH) {
            state.huhVolumePercent = clamped;
        } else {
            state.volumePercent = clamped;
        }
    }

    public int getCatSizePx(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        int raw = reaction == CatReaction.HUH ? state.huhCatSizePx : state.catSizePx;
        return clamp(raw, MIN_CAT_SIZE_PX, MAX_CAT_SIZE_PX);
    }

    public void setCatSizePx(CatReaction reaction, int catSizePx) {
        Objects.requireNonNull(reaction, "reaction");
        int clamped = clamp(catSizePx, MIN_CAT_SIZE_PX, MAX_CAT_SIZE_PX);
        if (reaction == CatReaction.HUH) {
            state.huhCatSizePx = clamped;
        } else {
            state.catSizePx = clamped;
        }
    }

    public double getCatPosX(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        return CatPlacement.clampNormalized(reaction == CatReaction.HUH ? state.huhCatPosX : state.catPosX);
    }

    public void setCatPosX(CatReaction reaction, double catPosX) {
        Objects.requireNonNull(reaction, "reaction");
        double clamped = CatPlacement.clampNormalized(catPosX);
        if (reaction == CatReaction.HUH) {
            state.huhCatPosX = clamped;
        } else {
            state.catPosX = clamped;
        }
    }

    public double getCatPosY(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        return CatPlacement.clampNormalized(reaction == CatReaction.HUH ? state.huhCatPosY : state.catPosY);
    }

    public void setCatPosY(CatReaction reaction, double catPosY) {
        Objects.requireNonNull(reaction, "reaction");
        double clamped = CatPlacement.clampNormalized(catPosY);
        if (reaction == CatReaction.HUH) {
            state.huhCatPosY = clamped;
        } else {
            state.catPosY = clamped;
        }
    }

    public CatAnchor getCatAnchor(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        CatAnchor anchor = reaction == CatReaction.HUH ? state.huhCatAnchor : state.catAnchor;
        return anchor != null ? anchor : CatAnchor.WINDOW;
    }

    public void setCatAnchor(CatReaction reaction, CatAnchor catAnchor) {
        Objects.requireNonNull(reaction, "reaction");
        CatAnchor resolved = catAnchor != null ? catAnchor : CatAnchor.WINDOW;
        if (reaction == CatReaction.HUH) {
            state.huhCatAnchor = resolved;
        } else {
            state.catAnchor = resolved;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
