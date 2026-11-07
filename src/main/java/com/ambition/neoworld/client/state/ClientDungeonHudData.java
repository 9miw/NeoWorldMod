package com.ambition.neoworld.client.state;

import com.ambition.neoworld.network.dungeon.DungeonHudPayload;

public final class ClientDungeonHudData {
    private static DungeonHudPayload state = DungeonHudPayload.HIDDEN;
    private ClientDungeonHudData() {}
    public static DungeonHudPayload get() { return state; }
    public static void update(DungeonHudPayload payload) { state = payload; }
    public static void reset() { state = DungeonHudPayload.HIDDEN; }
}
