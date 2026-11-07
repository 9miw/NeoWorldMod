package com.ambition.neoworld.client.gui.common;

/** Logical GUI coordinates shared by rendering and input bounds. */
public record NeoWindowLayout(int x, int y, int width, int height) {
    public static NeoWindowLayout centered(int viewportWidth, int viewportHeight, int desiredWidth, int desiredHeight) {
        int vw = Math.max(1, viewportWidth), vh = Math.max(1, viewportHeight);
        int margin = Math.min(12, Math.min(vw, vh) / 8);
        int w = Math.clamp(desiredWidth, 1, Math.max(1, vw - margin * 2));
        int h = Math.clamp(desiredHeight, 1, Math.max(1, vh - margin * 2));
        return new NeoWindowLayout((vw - w) / 2, (vh - h) / 2, w, h);
    }
    public int closeSize() { return Math.min(20, Math.min(width, height)); }
    public int closeX() { return x + width - closeSize() - inset(); }
    public int closeY() { return y + inset(); }
    private int inset() { return Math.min(6, Math.max(0, (Math.min(width, height) - closeSize()) / 2)); }
    public int contentX() { return x + Math.min(12, width / 2); }
    public int contentY() { return y + Math.min(40, height); }
    public int contentWidth() { return Math.max(0, width - 24); }
    public int contentHeight() { return Math.max(0, height - 52); }
}
