package com.catclapping.jetbrains;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Displays the animated clapping cat at the configured position in the active editor or IDE window.
 * The animation is driven by the audio position so clapping and sound stay in sync.
 */
public final class CatOverlay {

    private static final int EDGE_MARGIN_PX = 8;
    private static final int TICK_MS = 10;
    private static final int PREBUFFER_FRAMES = 4;
    private static final long MAX_PREBUFFER_WAIT_MS = 3000L;
    private static final long MAX_DISPLAY_MS = 20_000L;

    private CatOverlay() {
    }

    /**
     * Shows the cat using saved settings. If {@code playback} is not null it is started at the same moment.
     * Must be called on the EDT.
     */
    public static boolean show(Project project, SoundPlayer.Playback playback) {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        return showOverlay(
                project,
                playback,
                settings.getCatSizePx(),
                settings.getCatPosX(),
                settings.getCatPosY(),
                settings.getCatAnchor(),
                null) != null;
    }

    /**
     * Shows the cat with explicit placement parameters and returns an idempotent cleanup handle, or null if not shown.
     * Must be called on the EDT.
     */
    static Runnable showOverlay(
            Project project,
            SoundPlayer.Playback playback,
            int desiredSizePx,
            double posX,
            double posY,
            CatAnchor anchorType,
            Runnable onFinished) {
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null || !frame.isShowing()) {
            return null;
        }
        JRootPane rootPane = frame.getRootPane();
        if (rootPane == null) {
            return null;
        }
        int[] raw = CatAnimation.rawSize();
        if (raw == null) {
            return null;
        }

        int rawW = Math.max(raw[0], 1);
        int rawH = Math.max(raw[1], 1);
        JLayeredPane layeredPane = rootPane.getLayeredPane();
        int desiredH = Math.max(CatClappingSettings.MIN_CAT_SIZE_PX,
                Math.min(CatClappingSettings.MAX_CAT_SIZE_PX, desiredSizePx));
        int availableH = layeredPane.getHeight() > 0 ? layeredPane.getHeight() - 2 * EDGE_MARGIN_PX : desiredH;
        int maxH = Math.max(Math.min(desiredH, availableH), 32);
        double scale = (double) maxH / rawH;
        int targetW = Math.max((int) Math.round(rawW * scale), 16);
        int targetH = Math.max((int) Math.round(rawH * scale), 32);

        // Decode at device resolution so the cat stays sharp on HiDPI / Retina screens.
        double deviceScale = 1.0;
        GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        if (gc != null) {
            deviceScale = Math.max(gc.getDefaultTransform().getScaleX(), 1.0);
        }
        int pixelW = Math.min((int) Math.round(targetW * deviceScale), rawW);
        int pixelH = Math.min((int) Math.round(targetH * deviceScale), rawH);

        final long[] wallStartNanos = {-1L};
        LongSupplier clockMs = () -> {
            if (playback != null) {
                return playback.positionMs();
            }
            long start = wallStartNanos[0];
            return start < 0 ? -1L : (System.nanoTime() - start) / 1_000_000L;
        };

        CatAnimation animation = new CatAnimation(pixelW, pixelH, clockMs);

        final BufferedImage[] current = {null};
        JComponent overlay = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage img = current[0];
                if (img == null) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                } finally {
                    g2.dispose();
                }
            }
        };
        overlay.setOpaque(false);

        CatAnchor resolvedAnchor = anchorType != null ? anchorType : CatAnchor.WINDOW;
        Component anchor = resolvedAnchor == CatAnchor.WINDOW
                ? layeredPane
                : findAnchorComponent(project, layeredPane);
        Point location = computeLocation(posX, posY, anchor, layeredPane, targetW, targetH);
        overlay.setBounds(location.x, location.y, targetW, targetH);
        layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();

        final long createdNanos = System.nanoTime();
        final boolean[] started = {false};
        final long[] lastFrameEndMs = {0L};
        final AtomicBoolean cleanedUp = new AtomicBoolean(false);
        Timer timer = new Timer(TICK_MS, null);
        Runnable cleanup = () -> {
            if (!cleanedUp.compareAndSet(false, true)) {
                return;
            }
            timer.stop();
            animation.stop();
            if (playback != null) {
                playback.stop();
            }
            layeredPane.remove(overlay);
            layeredPane.revalidate();
            layeredPane.repaint(overlay.getBounds());
            if (onFinished != null) {
                onFinished.run();
            }
        };

        timer.addActionListener(e -> {
            long sinceCreatedMs = (System.nanoTime() - createdNanos) / 1_000_000L;
            if (sinceCreatedMs > MAX_DISPLAY_MS || !layeredPane.isShowing() || !overlay.isShowing()) {
                cleanup.run();
                return;
            }
            if (!started[0]) {
                boolean ready = animation.bufferedFrames() >= PREBUFFER_FRAMES || animation.isFinished();
                if (!ready && sinceCreatedMs < MAX_PREBUFFER_WAIT_MS) {
                    return;
                }
                if (animation.isFailed() && animation.peek() == null) {
                    cleanup.run();
                    return;
                }
                started[0] = true;
                wallStartNanos[0] = System.nanoTime();
                if (playback != null) {
                    playback.start();
                }
            }

            long now = Math.max(clockMs.getAsLong(), 0L);
            CatAnimation.Frame toShow = null;
            if (current[0] == null) {
                toShow = animation.poll();
            }
            CatAnimation.Frame next;
            while ((next = animation.peek()) != null && next.startMs <= now) {
                toShow = animation.poll();
            }
            if (toShow != null) {
                current[0] = toShow.image;
                lastFrameEndMs[0] = toShow.endMs;
                overlay.repaint();
            }

            if (animation.isFinished() && animation.peek() == null && now >= lastFrameEndMs[0]) {
                cleanup.run();
            }
        });
        timer.setCoalesce(true);
        animation.start();
        timer.start();
        return cleanup;
    }

    private static Component findAnchorComponent(Project project, JLayeredPane fallback) {
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        Editor textEditor = editorManager.getSelectedTextEditor();
        if (textEditor != null && textEditor.getComponent().isShowing()) {
            return textEditor.getComponent();
        }
        FileEditor fileEditor = editorManager.getSelectedEditor();
        if (fileEditor != null && fileEditor.getComponent().isShowing()) {
            return fileEditor.getComponent();
        }
        return fallback;
    }

    private static Point computeLocation(
            double posX,
            double posY,
            Component anchor,
            JLayeredPane layeredPane,
            int width,
            int height) {
        if (anchor == layeredPane || !anchor.isShowing()) {
            return CatPlacement.computePoint(
                    posX, posY,
                    0, 0, layeredPane.getWidth(), layeredPane.getHeight(),
                    layeredPane.getWidth(), layeredPane.getHeight(),
                    width, height, EDGE_MARGIN_PX);
        }
        Point anchorOrigin = SwingUtilities.convertPoint(anchor, 0, 0, layeredPane);
        return CatPlacement.computePoint(
                posX, posY,
                anchorOrigin.x, anchorOrigin.y, anchor.getWidth(), anchor.getHeight(),
                layeredPane.getWidth(), layeredPane.getHeight(),
                width, height, EDGE_MARGIN_PX);
    }
}
