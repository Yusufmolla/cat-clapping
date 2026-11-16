package com.catclapping.jetbrains;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates cooldown-throttled celebration overlays and audio for a project.
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

    public void celebrate() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        trigger(false,
                settings.isSoundEnabled(),
                settings.getVolumePercent(),
                settings.getCatSizePx(),
                settings.getCatPosX(),
                settings.getCatPosY(),
                settings.getCatAnchor());
    }

    void celebratePreview(
            boolean soundEnabled,
            int volumePercent,
            int catSizePx,
            double catPosX,
            double catPosY,
            CatAnchor catAnchor) {
        trigger(true, soundEnabled, volumePercent, catSizePx, catPosX, catPosY, catAnchor);
    }

    private void trigger(
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
            CatAnimation.rawSize();
            SoundPlayer.Playback playback = soundEnabled ? SoundPlayer.prepare(volumePercent) : null;
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
