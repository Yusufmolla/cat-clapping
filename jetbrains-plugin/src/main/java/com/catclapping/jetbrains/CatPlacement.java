package com.catclapping.jetbrains;

import java.awt.Point;

/**
 * Computes overlay coordinates from normalized (0.0 .. 1.0) horizontal and vertical positions.
 */
public final class CatPlacement {

    private CatPlacement() {
    }

    public static double clampNormalized(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Computes the top-left point for an overlay inside {@code containerW x containerH},
     * placed at normalized coordinates {@code (posX, posY)} inside the anchor rectangle
     * {@code (anchorX, anchorY, anchorW, anchorH)} with {@code marginPx} padding and clamped
     * to the container bounds.
     */
    public static Point computePoint(
            double posX,
            double posY,
            int anchorX,
            int anchorY,
            int anchorW,
            int anchorH,
            int containerW,
            int containerH,
            int width,
            int height,
            int marginPx) {
        double nx = clampNormalized(posX);
        double ny = clampNormalized(posY);

        int usableW = anchorW - width - 2 * marginPx;
        int x = usableW >= 0
                ? anchorX + marginPx + (int) Math.round(nx * usableW)
                : anchorX + (anchorW - width) / 2;

        int usableH = anchorH - height - 2 * marginPx;
        int y = usableH >= 0
                ? anchorY + marginPx + (int) Math.round(ny * usableH)
                : anchorY + (anchorH - height) / 2;

        int maxX = Math.max(containerW - width, 0);
        int maxY = Math.max(containerH - height, 0);
        return new Point(
                Math.max(0, Math.min(x, maxX)),
                Math.max(0, Math.min(y, maxY)));
    }
}
