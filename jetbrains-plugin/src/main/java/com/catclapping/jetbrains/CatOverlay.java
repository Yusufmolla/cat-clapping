package com.catclapping.jetbrains;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.net.URL;

/**
 * Displays the animated clapping cat GIF at the top center of the active editor (or IDE frame).
 */
public final class CatOverlay {

    private static final Logger LOG = Logger.getInstance(CatOverlay.class);
    private static final String GIF_RESOURCE = "/media/cat-clapping.gif";
    private static final int DISPLAY_DURATION_MS = 7000;
    private static final int MAX_SIZE_PX = 84;
    private static final int TOP_MARGIN_PX = 8;

    private CatOverlay() {
    }

    public static boolean show(Project project) {
        JFrame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null || !frame.isShowing()) {
            return false;
        }
        JRootPane rootPane = frame.getRootPane();
        if (rootPane == null) {
            return false;
        }
        URL gifUrl = CatOverlay.class.getResource(GIF_RESOURCE);
        if (gifUrl == null) {
            LOG.warn("GIF resource not found: " + GIF_RESOURCE);
            return false;
        }

        ImageIcon icon = new ImageIcon(gifUrl);
        int rawW = Math.max(icon.getIconWidth(), 1);
        int rawH = Math.max(icon.getIconHeight(), 1);
        double scale = Math.min((double) MAX_SIZE_PX / rawW, (double) MAX_SIZE_PX / rawH);
        int targetW = Math.max((int) Math.round(rawW * scale), 32);
        int targetH = Math.max((int) Math.round(rawH * scale), 32);

        JComponent overlay = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    Image img = icon.getImage();
                    g2.drawImage(img, 0, 0, getWidth(), getHeight(), this);
                } finally {
                    g2.dispose();
                }
            }
        };
        overlay.setOpaque(false);

        JLayeredPane layeredPane = rootPane.getLayeredPane();
        Component anchor = findAnchorComponent(project, layeredPane);
        Point topCenter = computeTopCenter(anchor, layeredPane, targetW);
        overlay.setBounds(topCenter.x, topCenter.y, targetW, targetH);

        layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();
        layeredPane.repaint();

        Timer timer = new Timer(DISPLAY_DURATION_MS, e -> {
            layeredPane.remove(overlay);
            layeredPane.revalidate();
            layeredPane.repaint();
        });
        timer.setRepeats(false);
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

    private static Point computeTopCenter(Component anchor, JLayeredPane layeredPane, int width) {
        if (anchor == layeredPane || !anchor.isShowing()) {
            int x = Math.max((layeredPane.getWidth() - width) / 2, 0);
            return new Point(x, TOP_MARGIN_PX);
        }
        Point point = SwingUtilities.convertPoint(anchor, (anchor.getWidth() - width) / 2, TOP_MARGIN_PX, layeredPane);
        int clampedX = Math.max(0, Math.min(point.x, Math.max(layeredPane.getWidth() - width, 0)));
        int clampedY = Math.max(0, Math.min(point.y, Math.max(layeredPane.getHeight() - MAX_SIZE_PX, 0)));
        return new Point(clampedX, clampedY);
    }
}
