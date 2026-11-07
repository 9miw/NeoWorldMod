package com.ambition.neoworld.client.gui.dungeon;

/** Native GUI-unit spacing shared by the illustrated route, boss strip and rewards. */
public record DungeonBrowserRouteLayout(int headingTop, int routeTop, int iconSize, boolean inlineCaptions,
        int bossTop, int bossHeight, int rewardHeadingTop, int rewardY) {
    public DungeonBrowserRouteLayout fillTo(int contentBottom, int rewardSlot) {
        int extra = Math.max(0, contentBottom - rewardSlot - 10 - rewardY);
        return new DungeonBrowserRouteLayout(headingTop < 0 ? -1 : headingTop + extra / 8,
                routeTop + extra / 4, iconSize, inlineCaptions, bossTop + extra / 2,
                bossHeight + extra / 8, rewardHeadingTop + extra, rewardY + extra);
    }

    public static DungeonBrowserRouteLayout fit(int metadataBottom, int panelHeight, int lineHeight) {
        boolean compact = panelHeight < 260;
        int heading = compact ? -1 : metadataBottom + 10;
        int route = compact ? metadataBottom + 7 : heading + lineHeight + 5;
        int size = compact ? 16 : panelHeight < 330 ? 18 : 24;
        int boss = route + size + (compact ? 8 : 3 + lineHeight + 8);
        int bossHeight = Math.max(compact ? 18 : 20, lineHeight + 8);
        int rewards = boss + bossHeight + (compact ? 8 : 10);
        return new DungeonBrowserRouteLayout(heading, route, size, compact, boss, bossHeight,
                rewards, rewards + lineHeight + 3);
    }
}
