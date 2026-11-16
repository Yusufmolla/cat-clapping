package com.catclapping.jetbrains;

import java.awt.Point;

/**
 * Screen/editor overlay positions for the clapping cat.
 */
public enum CatPosition {
    TOP_LEFT("Top Left", Horizontal.LEFT, Vertical.TOP),
    TOP_CENTER("Top Center", Horizontal.CENTER, Vertical.TOP),
    TOP_RIGHT("Top Right", Horizontal.RIGHT, Vertical.TOP),
    MIDDLE_LEFT("Middle Left", Horizontal.LEFT, Vertical.MIDDLE),
    CENTER("Center", Horizontal.CENTER, Vertical.MIDDLE),
    MIDDLE_RIGHT("Middle Right", Horizontal.RIGHT, Vertical.MIDDLE),
    BOTTOM_LEFT("Bottom Left", Horizontal.LEFT, Vertical.BOTTOM),
    BOTTOM_CENTER("Bottom Center", Horizontal.CENTER, Vertical.BOTTOM),
    BOTTOM_RIGHT("Bottom Right", Horizontal.RIGHT, Vertical.BOTTOM);

    enum Horizontal {
        LEFT, CENTER, RIGHT
    }

    enum Vertical {
        TOP, MIDDLE, BOTTOM
    }

    private final String displayName;
    private final Horizontal horizontal;
    private final Vertical vertical;

    CatPosition(String displayName, Horizontal horizontal, Vertical vertical) {
        this.displayName = displayName;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Computes the top-left point for an overlay inside {@code containerW x containerH},
     * aligned to the anchor rectangle {@code (anchorX, anchorY, anchorW, anchorH)} with {@code marginPx}
     * padding and clamped to the container bounds.
     */
    public Point computePoint(
            int anchorX,
            int anchorY,
            int anchorW,
            int anchorH,
            int containerW,
            int containerH,
            int width,
            int height,
            int marginPx) {
        int x = switch (horizontal) {
            case LEFT -> anchorX + marginPx;
            case CENTER -> anchorX + (anchorW - width) / 2;
            case RIGHT -> anchorX + anchorW - width - marginPx;
        };
        int y = switch (vertical) {
            case TOP -> anchorY + marginPx;
            case MIDDLE -> anchorY + (anchorH - height) / 2;
            case BOTTOM -> anchorY + anchorH - height - marginPx;
        };
        int maxX = Math.max(containerW - width, 0);
        int maxY = Math.max(containerH - height, 0);
        int clampedX = Math.max(0, Math.min(x, maxX));
        int clampedY = Math.max(0, Math.min(y, maxY));
        return new Point(clampedX, clampedY);
    }
}
