package com.catclapping.jetbrains;

/**
 * Available cat reactions triggered by IDE/terminal execution outcomes.
 */
public enum CatReaction {
    CLAPPING("Clapping Cat", "/media/cat-clapping.gif", "/media/cat-clapping.wav", 520, 576, 1024, 0),
    HUH("HUH Cat", "/media/cat-huh.gif", "/media/cat-huh.wav", 320, 404, 540, 92);

    private final String displayName;
    private final String gifResource;
    private final String audioResource;
    private final int defaultSizePx;
    private final int fallbackRawWidth;
    private final int fallbackRawHeight;
    private final int previewFrameIndex;

    CatReaction(
            String displayName,
            String gifResource,
            String audioResource,
            int defaultSizePx,
            int fallbackRawWidth,
            int fallbackRawHeight,
            int previewFrameIndex) {
        this.displayName = displayName;
        this.gifResource = gifResource;
        this.audioResource = audioResource;
        this.defaultSizePx = defaultSizePx;
        this.fallbackRawWidth = fallbackRawWidth;
        this.fallbackRawHeight = fallbackRawHeight;
        this.previewFrameIndex = previewFrameIndex;
    }

    public String displayName() {
        return displayName;
    }

    public String gifResource() {
        return gifResource;
    }

    public String audioResource() {
        return audioResource;
    }

    public int defaultSizePx() {
        return defaultSizePx;
    }

    public int fallbackRawWidth() {
        return fallbackRawWidth;
    }

    public int fallbackRawHeight() {
        return fallbackRawHeight;
    }

    public int previewFrameIndex() {
        return previewFrameIndex;
    }
}
