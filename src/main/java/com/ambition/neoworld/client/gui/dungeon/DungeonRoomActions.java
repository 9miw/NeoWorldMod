package com.ambition.neoworld.client.gui.dungeon;

/** Visibility policy only; the server still authorizes every room and party action. */
public record DungeonRoomActions(boolean start, boolean ready, boolean leave, boolean disband, boolean party) {
    public static DungeonRoomActions forMember(boolean member, boolean owner, int capacity) {
        if (!member) return new DungeonRoomActions(false, false, false, false, false);
        boolean teamOwner = owner && capacity > 1;
        return new DungeonRoomActions(owner, !owner, !teamOwner, teamOwner, teamOwner);
    }
}
