package com.catclapping.jetbrains;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import javax.swing.SwingUtilities;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates cooldown-throttled cat reaction overlays and audio for a project.
 */
@Service(Service.Level.PROJECT)
public final class CelebrationService implements Disposable {

    private static final long COOLDOWN_MS = 1500L;

    private final Project project;
    private final AtomicLong lastShownMs = new AtomicLong(0L);
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicReference<Runnable> activeCleanup = new AtomicReference<>();

    public CelebrationService(Project project) {
        this.project = project;
    }

    public static CelebrationService getInstance(Project project) {
        return project.getService(CelebrationService.class);
    }

    public void trigger(CatReaction reaction) {
        Objects.requireNonNull(reaction, "reaction");
        CatClappingSettings settings = CatClappingSettings.getInstance();
        if (!settings.isEnabled(reaction)) {
            return;
        }
        long clipStartMs = reaction == CatReaction.HUH ? settings.getHuhClipMode().clipStartMs() : 0L;
        long clipEndMs = reaction == CatReaction.HUH ? settings.getHuhClipMode().clipEndMs() : -1L;
        triggerInternal(
                reaction,
                clipStartMs,
                clipEndMs,
                false,
                settings.isSoundEnabled(reaction),
                settings.getVolumePercent(reaction),
                settings.getCatSizePx(reaction),
                settings.getCatPosX(reaction),
                settings.getCatPosY(reaction),
                settings.getCatAnchor(reaction));
    }

    void triggerPreview(
            CatReaction reaction,
            long clipStartMs,
            long clipEndMs,
            boolean soundEnabled,
            int volumePercent,
            int catSizePx,
            double catPosX,
            double catPosY,
            CatAnchor catAnchor) {
        Objects.requireNonNull(reaction, "reaction");
        triggerInternal(
                reaction,
                clipStartMs,
                clipEndMs,
                true,
                soundEnabled,
                volumePercent,
                catSizePx,
                catPosX,
                catPosY,
                catAnchor);
    }

    private void triggerInternal(
            CatReaction reaction,
            long clipStartMs,
            long clipEndMs,
            boolean ignoreCooldown,
            boolean soundEnabled,
            int volumePercent,
            int catSizePx,
            double catPosX,
            double catPosY,
            CatAnchor catAnchor) {
        if (isDisposed()) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastShownMs.get();
        if (!ignoreCooldown && (now - previous) < COOLDOWN_MS) {
            return;
        }
        if (!ignoreCooldown && !lastShownMs.compareAndSet(previous, now)) {
            return;
        }
        if (ignoreCooldown) {
            lastShownMs.set(now);
        }

        // Load the GIF and open the audio line off the EDT, then start both together.
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (isDisposed()) {
                return;
            }
            CatAnimation.rawSize(reaction);
            SoundPlayer.Playback playback = soundEnabled
                    ? SoundPlayer.prepare(reaction, clipStartMs, clipEndMs, volumePercent)
                    : null;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (isDisposed()) {
                    if (playback != null) {
                        playback.stop();
                    }
                    return;
                }
                Runnable previousOverlay = activeCleanup.getAndSet(null);
                if (previousOverlay != null) {
                    previousOverlay.run();
                }
                final Runnable[] holder = {null};
                Runnable cleanup = CatOverlay.showOverlay(
                        project,
                        reaction,
                        clipStartMs,
                        clipEndMs,
                        playback,
                        catSizePx,
                        catPosX,
                        catPosY,
                        catAnchor,
                        () -> activeCleanup.compareAndSet(holder[0], null));
                holder[0] = cleanup;
                if (cleanup != null) {
                    activeCleanup.set(cleanup);
                } else if (playback != null) {
                    playback.stop();
                }
            });
        });
    }

    public boolean isDisposed() {
        return disposed.get() || project.isDisposed();
    }

    @Override
    public void dispose() {
        disposed.set(true);
        Runnable cleanup = activeCleanup.getAndSet(null);
        if (cleanup != null) {
            if (SwingUtilities.isEventDispatchThread()) {
                cleanup.run();
            } else {
                SwingUtilities.invokeLater(cleanup);
            }
        }
    }
}
