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
import java.util.function.LongSupplier;

/**
 * Displays the animated clapping cat at the top center of the active editor (or IDE frame).
 * The animation is driven by the audio position so clapping and sound stay in sync.
 */
public final class CatOverlay {

    private static final int TOP_MARGIN_PX = 8;
    private static final int TICK_MS = 10;
    private static final int PREBUFFER_FRAMES = 4;
    private static final long MAX_PREBUFFER_WAIT_MS = 3000L;
    private static final long MAX_DISPLAY_MS = 20_000L;

    private CatOverlay() {
    }

    /**
     * Shows the cat. If {@code playback} is not null it is started at the same moment the animation starts.
     * Must be called on the EDT.
     */
    public static boolean show(Project project, SoundPlayer.Playback playback) {
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null || !frame.isShowing()) {
            return false;
        }
        JRootPane rootPane = frame.getRootPane();
        if (rootPane == null) {
            return false;
        }
        int[] raw = CatAnimation.rawSize();
        if (raw == null) {
            return false;
        }

        int rawW = Math.max(raw[0], 1);
        int rawH = Math.max(raw[1], 1);
        JLayeredPane layeredPane = rootPane.getLayeredPane();
        int desiredH = CatClappingSettings.getInstance().getCatSizePx();
        int availableH = layeredPane.getHeight() > 0 ? layeredPane.getHeight() - 2 * TOP_MARGIN_PX : desiredH;
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

        Component anchor = findAnchorComponent(project, layeredPane);
        Point topCenter = computeTopCenter(anchor, layeredPane, targetW, targetH);
        overlay.setBounds(topCenter.x, topCenter.y, targetW, targetH);
        layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();

        final long createdNanos = System.nanoTime();
        final boolean[] started = {false};
        final long[] lastFrameEndMs = {0L};
        Timer timer = new Timer(TICK_MS, null);
        Runnable cleanup = () -> {
            timer.stop();
            animation.stop();
            if (!started[0] && playback != null) {
                playback.discard();
            }
            layeredPane.remove(overlay);
            layeredPane.revalidate();
            layeredPane.repaint(overlay.getBounds());
        };

        timer.addActionListener(e -> {
            long sinceCreatedMs = (System.nanoTime() - createdNanos) / 1_000_000L;
            if (sinceCreatedMs > MAX_DISPLAY_MS || !overlay.isShowing() && started[0]) {
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
        return true;
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

    private static Point computeTopCenter(Component anchor, JLayeredPane layeredPane, int width, int height) {
        if (anchor == layeredPane || !anchor.isShowing()) {
            int x = Math.max((layeredPane.getWidth() - width) / 2, 0);
            return new Point(x, TOP_MARGIN_PX);
        }
        Point point = SwingUtilities.convertPoint(anchor, (anchor.getWidth() - width) / 2, TOP_MARGIN_PX, layeredPane);
        int clampedX = Math.max(0, Math.min(point.x, Math.max(layeredPane.getWidth() - width, 0)));
        int clampedY = Math.max(0, Math.min(point.y, Math.max(layeredPane.getHeight() - height, 0)));
        return new Point(clampedX, clampedY);
    }
}
