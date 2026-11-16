package com.catclapping.jetbrains;

/**
 * Reference area for positioning the clapping cat overlay.
 */
public enum CatAnchor {
    EDITOR("Active Editor"),
    WINDOW("IDE Window");

    private final String displayName;

    CatAnchor(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
