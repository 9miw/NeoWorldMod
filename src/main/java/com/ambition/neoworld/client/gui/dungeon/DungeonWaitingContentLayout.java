package com.ambition.neoworld.client.gui.dungeon;

/** Measured native-unit sections; three member rows remain visible before list scrolling. */
public record DungeonWaitingContentLayout(int levelTop, int timeTop, int capacityTop,
        int previewHeight, int metadataDivider, int membersHeading, int tableHeading,
        int memberTop, int rowHeight, int statusTop, int rewardDivider, int rewardHeading,
        int rewardTop, int slotSize, int contentHeight) {
    public int memberHeight() { return rowHeight * 3; }

    public static DungeonWaitingContentLayout fit(int availableHeight) {
        if (availableHeight < 280)
            return new DungeonWaitingContentLayout(28,40,52,60,64,70,82,94,18,152,164,170,184,20,220);
        return new DungeonWaitingContentLayout(36,52,68,82,88,98,110,122,22,192,210,222,240,24,280);
    }
}
