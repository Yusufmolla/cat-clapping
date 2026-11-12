package com.catclapping.jetbrains;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates cooldown-throttled celebration overlays and audio for a project.
 */
@Service(Service.Level.PROJECT)
public final class CelebrationService implements Disposable {

    private static final long COOLDOWN_MS = 1500L;

    private final Project project;
    private final AtomicLong lastShownMs = new AtomicLong(0L);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public CelebrationService(Project project) {
        this.project = project;
    }

    public static CelebrationService getInstance(Project project) {
        return project.getService(CelebrationService.class);
    }

    public void celebrate() {
        celebrate(false);
    }

    public void celebrate(boolean ignoreCooldown) {
        if (project.isDisposed()) {
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
            CatAnimation.rawSize();
            SoundPlayer.Playback playback = CatClappingSettings.getInstance().isSoundEnabled()
                    ? SoundPlayer.prepare()
                    : null;
            ApplicationManager.getApplication().invokeLater(() -> {
                boolean shown = !project.isDisposed() && CatOverlay.show(project, playback);
                if (!shown && playback != null) {
                    playback.discard();
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
    }
}
