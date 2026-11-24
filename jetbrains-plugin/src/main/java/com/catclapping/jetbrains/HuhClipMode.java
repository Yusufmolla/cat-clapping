package com.catclapping.jetbrains;

/**
 * Selectable clip window for the HUH Cat error reaction.
 */
public enum HuhClipMode {
    SECOND_HUH("2nd HUH only (Default)", 3950L, 5250L),
    FIRST_HUH("1st HUH only", 0L, 1500L),
    FULL("Full (both HUHs)", 0L, -1L);

    private final String displayName;
    private final long clipStartMs;
    private final long clipEndMs;

    HuhClipMode(String displayName, long clipStartMs, long clipEndMs) {
        this.displayName = displayName;
        this.clipStartMs = clipStartMs;
        this.clipEndMs = clipEndMs;
    }

    public String displayName() {
        return displayName;
    }

    public long clipStartMs() {
        return clipStartMs;
    }

    public long clipEndMs() {
        return clipEndMs;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
