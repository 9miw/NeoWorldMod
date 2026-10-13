package com.ambition.neoworld.client.state;

import com.ambition.neoworld.network.pvp.PvpQueueStatePayload;
public final class ClientPvpQueueData {
    private static PvpQueueStatePayload state=PvpQueueStatePayload.EMPTY;
    private ClientPvpQueueData() { }
    public static PvpQueueStatePayload state() { return state; }
    public static void update(PvpQueueStatePayload payload) { state=payload; }
    public static void reset() { state=PvpQueueStatePayload.EMPTY; }
}
