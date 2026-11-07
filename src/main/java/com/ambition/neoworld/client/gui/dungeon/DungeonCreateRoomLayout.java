package com.ambition.neoworld.client.gui.dungeon;

/** Native GUI-unit geometry, kept independent of the client for viewport regression checks. */
public record DungeonCreateRoomLayout(int width, int height, int bodyTop, int bodyHeight,
        int bodyWidth, int footerTop, float spacing, int rewardWidth, int slotSize, int contentHeight) {
    public static DungeonCreateRoomLayout fit(int viewportWidth, int viewportHeight) {
        int w = Math.max(1, Math.min(560, Math.min(viewportWidth - 12, (viewportHeight - 12) * 4 / 3)));
        int h = Math.max(1, w * 3 / 4);
        int top = Math.max(44, Math.round(h / 6F)), footer = h - 42;
        int bodyHeight = Math.max(1, footer - top - 12), bodyWidth = Math.max(1, w - 60);
        float spacing = Math.clamp((bodyHeight - 6) / 224F, 1F, 1.3F);
        int rewardWidth = Math.max(18, bodyWidth - 12), slot = Math.clamp((rewardWidth - 20) / 6, 18, 40);
        return new DungeonCreateRoomLayout(w, h, top, bodyHeight, bodyWidth, footer, spacing,
                rewardWidth, slot, Math.round(178 * spacing) + slot + 16);
    }

    public static int modeIndex(int mask) {
        return Integer.bitCount(mask) == 1 && (mask & ~7) == 0 ? Integer.numberOfTrailingZeros(mask) : -1;
    }

    public static DungeonCreateRoomLayout fitWaiting(int viewportWidth, int viewportHeight) {
        int w = Math.max(1, Math.min(734, viewportWidth - 48));
        int h = Math.max(1, Math.min(viewportHeight - 28, Math.max(280, Math.round(w * 417F / 734F))));
        int top = Math.max(44, Math.round(w * 120F / 1468F) + 8), footer = h - 42;
        int bodyHeight = Math.max(1, footer - top - 12), bodyWidth = Math.max(1, w - 60);
        int rewardWidth = Math.max(18, bodyWidth - 12), slot = Math.clamp((rewardWidth - 20) / 6, 18, 40);
        return new DungeonCreateRoomLayout(w, h, top, bodyHeight, bodyWidth, footer, 1F,
                rewardWidth, slot, 240 + slot + 16);
    }

    /** Selection-screen styling without changing the waiting room's existing geometry. */
    public static DungeonCreateRoomLayout fitV5(int viewportWidth, int viewportHeight) {
        int w = Math.max(1, Math.min(560, Math.min(viewportWidth - 48, (viewportHeight - 28) * 4 / 3)));
        int h = Math.max(1, w * 3 / 4);
        int top = Math.max(44, Math.round(w * 130F / 1468F) + 6), footer = h - 40;
        int bodyHeight = Math.max(1, footer - top - 12), bodyWidth = Math.max(1, w - 40);
        float spacing = Math.clamp((bodyHeight - 6) / 218F, 1F, 1.15F);
        int rewardWidth = Math.max(18, bodyWidth - 12), slot = Math.clamp((rewardWidth - 20) / 6, 18, 24);
        return new DungeonCreateRoomLayout(w, h, top, bodyHeight, bodyWidth, footer, spacing,
                rewardWidth, slot, Math.round(178 * spacing) + slot + 16);
    }
}
